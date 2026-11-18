{
  description = "Apk Çeviricisi Plus Ultra - Nix Reproducible Android Build Environment (CEZALI GENÇ SG)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "36" ];
          buildToolsVersions = [ "35.0.0" ];
        };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk17
            gradle
            proot
            bashInteractive
            zsh
            git
            unzip
            androidComposition.androidsdk
          ];

          ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";
          JAVA_HOME = "${pkgs.jdk17}";

          shellHook = ''
            echo "APK Editor Ultra (CEZALI GENÇ SG) NixOS ortamı hazır."
            echo "Derlemek için: gradle :app:assembleDebug"
          '';
        };
      });
}
