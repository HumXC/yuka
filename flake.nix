{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-23.11";
    nixpkgs-unstable.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, flake-utils, nixpkgs, nixpkgs-unstable }:
    flake-utils.lib.eachSystem [ "x86_64-linux" ] (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };
        pkgs-unstable = import nixpkgs-unstable {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };
        protocGenGrpcKotlinVersion = "1.4.1";
        protocGenGrpcKotlinJar = pkgs.fetchurl {
          url = "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-kotlin/${protocGenGrpcKotlinVersion}/protoc-gen-grpc-kotlin-${protocGenGrpcKotlinVersion}-jdk8.jar";
          hash = "sha256-YqmVa0yarUoG7Lu11CXgeKA5HsV8Wia7+7sZ9nF7zWk=";
        };
      in
      {
        devShells.default =
          let
            androidComposition = pkgs.androidenv.composeAndroidPackages {
              toolsVersion = "26.1.1";
              platformToolsVersion = "34.0.5";
              buildToolsVersions = [ "34.0.0" ];
              platformVersions = [ "21" "34" ];
              abiVersions = [ "arm64-v8a" ];
              includeSources = true;
            };
          in
          (pkgs.buildFHSUserEnv {
            name = "android-sdk-env";
            targetPkgs = pkgs: (with pkgs;
              [
                glibc
                (pkgs.writeScriptBin "studio" ''
                  ${pkgs.glib}/bin/gio launch ${pkgs.android-studio}/share/applications/android-studio.desktop .
                '')
                (pkgs.writeScriptBin "protoc-gen-kotlin" ''
                  #!/usr/bin/env sh
                  ${pkgs.jdk8}/bin/java -jar ${protocGenGrpcKotlinJar} "$@"
                '')
              ]) ++ (with pkgs-unstable; [
              android-studio
              protobuf_26
              zulu17
            ]) ++ [ androidComposition.androidsdk ];
            profile =
              ''
                export ANDROID_HOME=${androidComposition.androidsdk}/libexec/android-sdk
                echo ANDROID_HOME: $ANDROID_HOME
              '';
          }).env;
      }
    );
}
 
