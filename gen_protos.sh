#!/usr/bin/env sh
# protoc --proto_path=app/src/protos --java_out=app/src/main/java --kotlin_out=app/src/main/java/ app/src/protos/hello_world.proto
protoc --plugin=protoc-gen-grpckt=/usr/bin/protoc-gen-grpc-kotlin --java_out=app/src/main/java --grpckt_out=app/src/main/java --proto_path=app/src/protos app/src/protos/hello_world.proto
