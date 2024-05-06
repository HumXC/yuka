{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, flake-utils, nixpkgs }:
    flake-utils.lib.eachSystem [ "x86_64-linux" ] (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };
      in
      {
        devShells.default =
          let
            ndkVersions = "25.1.8937393";
            androidComposition = pkgs.androidenv.composeAndroidPackages {
              toolsVersion = "26.1.1";
              platformToolsVersion = "35.0.1";
              buildToolsVersions = [ "34.0.0" ];
              includeEmulator = false;
              emulatorVersion = "35.1.4";
              platformVersions = [ "34" ];
              abiVersions = [ "arm64-v8a" ];
              # includeNDK = true;
              # ndkVersions = [ ndkVersions ];
            };
          in
          (pkgs.buildFHSUserEnv {
            name = "android-sdk-env";
            targetPkgs = pkgs: (with pkgs;
              [
                glibc
                android-studio
                (pkgs.writeScriptBin "studio" ''${pkgs.glib}/bin/gio launch ${pkgs.android-studio}/share/applications/android-studio.desktop'')
              ]) ++ [ androidComposition.androidsdk ];
            profile =
              ''
                export ANDROID_HOME=${androidComposition.androidsdk}/libexec/android-sdk
                echo ANDROID_HOME: $ANDROID_HOME

                export SYSROOT=$ANDROID_HOME/ndk/${ndkVersions}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/
                echo SYSROOT: $SYSROOT

                export PATH=$PATH:$ANDROID_HOME/ndk/${ndkVersions}
                export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$ANDROID_HOME/platform-tools
                export CFLAGS="I$SYSROOT/usr/include"
                export LDFLAGS="-L$SYSROOT/usr/lib -lz"

              '';
          }).env;
      }
    );
}
 