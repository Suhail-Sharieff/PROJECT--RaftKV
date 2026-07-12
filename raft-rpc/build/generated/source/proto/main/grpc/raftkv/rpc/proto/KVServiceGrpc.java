package raftkv.rpc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: raft.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class KVServiceGrpc {

  private KVServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "raftkv.KVService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<raftkv.rpc.proto.PutRequest,
      raftkv.rpc.proto.PutResponse> getPutMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Put",
      requestType = raftkv.rpc.proto.PutRequest.class,
      responseType = raftkv.rpc.proto.PutResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<raftkv.rpc.proto.PutRequest,
      raftkv.rpc.proto.PutResponse> getPutMethod() {
    io.grpc.MethodDescriptor<raftkv.rpc.proto.PutRequest, raftkv.rpc.proto.PutResponse> getPutMethod;
    if ((getPutMethod = KVServiceGrpc.getPutMethod) == null) {
      synchronized (KVServiceGrpc.class) {
        if ((getPutMethod = KVServiceGrpc.getPutMethod) == null) {
          KVServiceGrpc.getPutMethod = getPutMethod =
              io.grpc.MethodDescriptor.<raftkv.rpc.proto.PutRequest, raftkv.rpc.proto.PutResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Put"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raftkv.rpc.proto.PutRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raftkv.rpc.proto.PutResponse.getDefaultInstance()))
              .setSchemaDescriptor(new KVServiceMethodDescriptorSupplier("Put"))
              .build();
        }
      }
    }
    return getPutMethod;
  }

  private static volatile io.grpc.MethodDescriptor<raftkv.rpc.proto.GetRequest,
      raftkv.rpc.proto.GetResponse> getGetMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Get",
      requestType = raftkv.rpc.proto.GetRequest.class,
      responseType = raftkv.rpc.proto.GetResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<raftkv.rpc.proto.GetRequest,
      raftkv.rpc.proto.GetResponse> getGetMethod() {
    io.grpc.MethodDescriptor<raftkv.rpc.proto.GetRequest, raftkv.rpc.proto.GetResponse> getGetMethod;
    if ((getGetMethod = KVServiceGrpc.getGetMethod) == null) {
      synchronized (KVServiceGrpc.class) {
        if ((getGetMethod = KVServiceGrpc.getGetMethod) == null) {
          KVServiceGrpc.getGetMethod = getGetMethod =
              io.grpc.MethodDescriptor.<raftkv.rpc.proto.GetRequest, raftkv.rpc.proto.GetResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Get"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raftkv.rpc.proto.GetRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raftkv.rpc.proto.GetResponse.getDefaultInstance()))
              .setSchemaDescriptor(new KVServiceMethodDescriptorSupplier("Get"))
              .build();
        }
      }
    }
    return getGetMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static KVServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<KVServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<KVServiceStub>() {
        @java.lang.Override
        public KVServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new KVServiceStub(channel, callOptions);
        }
      };
    return KVServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static KVServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<KVServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<KVServiceBlockingStub>() {
        @java.lang.Override
        public KVServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new KVServiceBlockingStub(channel, callOptions);
        }
      };
    return KVServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static KVServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<KVServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<KVServiceFutureStub>() {
        @java.lang.Override
        public KVServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new KVServiceFutureStub(channel, callOptions);
        }
      };
    return KVServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void put(raftkv.rpc.proto.PutRequest request,
        io.grpc.stub.StreamObserver<raftkv.rpc.proto.PutResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPutMethod(), responseObserver);
    }

    /**
     */
    default void get(raftkv.rpc.proto.GetRequest request,
        io.grpc.stub.StreamObserver<raftkv.rpc.proto.GetResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service KVService.
   */
  public static abstract class KVServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return KVServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service KVService.
   */
  public static final class KVServiceStub
      extends io.grpc.stub.AbstractAsyncStub<KVServiceStub> {
    private KVServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KVServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new KVServiceStub(channel, callOptions);
    }

    /**
     */
    public void put(raftkv.rpc.proto.PutRequest request,
        io.grpc.stub.StreamObserver<raftkv.rpc.proto.PutResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPutMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void get(raftkv.rpc.proto.GetRequest request,
        io.grpc.stub.StreamObserver<raftkv.rpc.proto.GetResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service KVService.
   */
  public static final class KVServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<KVServiceBlockingStub> {
    private KVServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KVServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new KVServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public raftkv.rpc.proto.PutResponse put(raftkv.rpc.proto.PutRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPutMethod(), getCallOptions(), request);
    }

    /**
     */
    public raftkv.rpc.proto.GetResponse get(raftkv.rpc.proto.GetRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service KVService.
   */
  public static final class KVServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<KVServiceFutureStub> {
    private KVServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KVServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new KVServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<raftkv.rpc.proto.PutResponse> put(
        raftkv.rpc.proto.PutRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPutMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<raftkv.rpc.proto.GetResponse> get(
        raftkv.rpc.proto.GetRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PUT = 0;
  private static final int METHODID_GET = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_PUT:
          serviceImpl.put((raftkv.rpc.proto.PutRequest) request,
              (io.grpc.stub.StreamObserver<raftkv.rpc.proto.PutResponse>) responseObserver);
          break;
        case METHODID_GET:
          serviceImpl.get((raftkv.rpc.proto.GetRequest) request,
              (io.grpc.stub.StreamObserver<raftkv.rpc.proto.GetResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getPutMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              raftkv.rpc.proto.PutRequest,
              raftkv.rpc.proto.PutResponse>(
                service, METHODID_PUT)))
        .addMethod(
          getGetMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              raftkv.rpc.proto.GetRequest,
              raftkv.rpc.proto.GetResponse>(
                service, METHODID_GET)))
        .build();
  }

  private static abstract class KVServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    KVServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return raftkv.rpc.proto.Raft.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("KVService");
    }
  }

  private static final class KVServiceFileDescriptorSupplier
      extends KVServiceBaseDescriptorSupplier {
    KVServiceFileDescriptorSupplier() {}
  }

  private static final class KVServiceMethodDescriptorSupplier
      extends KVServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    KVServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (KVServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new KVServiceFileDescriptorSupplier())
              .addMethod(getPutMethod())
              .addMethod(getGetMethod())
              .build();
        }
      }
    }
    return result;
  }
}
