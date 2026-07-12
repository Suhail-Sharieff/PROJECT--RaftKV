package raftkv.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import raftkv.rpc.proto.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RaftCliClient {
    private final Map<Integer, String> servers = new HashMap<>();
    private int currentServerId = -1;
    private ManagedChannel channel;
    private KVServiceGrpc.KVServiceBlockingStub blockingStub;

    public RaftCliClient(Map<Integer, String> servers) {
        this.servers.putAll(servers);
        if (!servers.isEmpty()) {
            this.currentServerId = servers.keySet().iterator().next();
        }
    }

    private void connect(int serverId) {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
        String address = servers.get(serverId);
        if (address == null) {
            throw new IllegalArgumentException("Unknown server ID: " + serverId);
        }
        String[] parts = address.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = KVServiceGrpc.newBlockingStub(channel);
        currentServerId = serverId;
    }

    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public void put(String key, String value) {
        if (currentServerId == -1) {
            System.err.println("No servers configured.");
            return;
        }

        int retries = 0;
        int maxRetries = servers.size();
        while (retries < maxRetries) {
            if (blockingStub == null) {
                connect(currentServerId);
            }
            try {
                System.out.println("Sending PUT " + key + "=" + value + " to Node " + currentServerId + "...");
                PutResponse response = blockingStub.withDeadlineAfter(3, TimeUnit.SECONDS)
                        .put(PutRequest.newBuilder().setKey(key).setValue(value).build());
                if (response.getSuccess()) {
                    System.out.println("SUCCESS: Put completed successfully.");
                    return;
                } else {
                    int leaderId = response.getLeaderId();
                    if (leaderId != -1 && servers.containsKey(leaderId)) {
                        System.out.println("REDIRECT: Node " + currentServerId + " is not the leader. Redirecting to Leader Node " + leaderId + "...");
                        connect(leaderId);
                    } else {
                        System.out.println("ERROR: Request failed, leader unknown. Trying next server...");
                        tryNextServer();
                    }
                }
            } catch (Exception e) {
                System.err.println("Communication error with Node " + currentServerId + ": " + e.getMessage());
                tryNextServer();
            }
            retries++;
        }
        System.err.println("FAILED: Could not complete PUT operation after trying all configured servers.");
    }

    public void get(String key) {
        if (currentServerId == -1) {
            System.err.println("No servers configured.");
            return;
        }

        int retries = 0;
        int maxRetries = servers.size();
        while (retries < maxRetries) {
            if (blockingStub == null) {
                connect(currentServerId);
            }
            try {
                System.out.println("Sending GET " + key + " to Node " + currentServerId + "...");
                GetResponse response = blockingStub.withDeadlineAfter(3, TimeUnit.SECONDS)
                        .get(GetRequest.newBuilder().setKey(key).build());
                if (response.getSuccess()) {
                    System.out.println("SUCCESS: Value for '" + key + "' is '" + response.getValue() + "'");
                    return;
                } else {
                    int leaderId = response.getLeaderId();
                    if (leaderId != -1 && servers.containsKey(leaderId)) {
                        System.out.println("REDIRECT: Node " + currentServerId + " redirected to Leader Node " + leaderId + "...");
                        connect(leaderId);
                    } else {
                        System.out.println("ERROR: Request failed. Trying next server...");
                        tryNextServer();
                    }
                }
            } catch (Exception e) {
                System.err.println("Communication error with Node " + currentServerId + ": " + e.getMessage());
                tryNextServer();
            }
            retries++;
        }
        System.err.println("FAILED: Could not complete GET operation after trying all configured servers.");
    }

    private void tryNextServer() {
        for (int serverId : servers.keySet()) {
            if (serverId != currentServerId) {
                connect(serverId);
                return;
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        String serversArg = args[0];
        String operation = args[1].toLowerCase();
        String key = args[2];
        String value = operation.equals("put") && args.length > 3 ? args[3] : "";

        Map<Integer, String> serverMap = new HashMap<>();
        try {
            String[] parts = serversArg.split(",");
            for (String part : parts) {
                String[] nodeAndAddr = part.split("=");
                serverMap.put(Integer.parseInt(nodeAndAddr[0].trim()), nodeAndAddr[1].trim());
            }
        } catch (Exception e) {
            System.err.println("Invalid servers argument: " + serversArg);
            printUsage();
            System.exit(1);
        }

        RaftCliClient client = new RaftCliClient(serverMap);
        try {
            if (operation.equals("put")) {
                client.put(key, value);
            } else if (operation.equals("get")) {
                client.get(key);
            } else {
                System.err.println("Unknown operation: " + operation);
                printUsage();
                System.exit(1);
            }
        } finally {
            client.shutdown();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp ... raftkv.client.RaftCliClient <servers> <operation> <key> [value]");
        System.out.println("  servers: comma-separated list of nodeId=host:port (e.g. 1=localhost:8001,2=localhost:8002)");
        System.out.println("  operation: put or get");
        System.out.println("  key: key to read or write");
        System.out.println("  value: value to write (required for put)");
    }
}
