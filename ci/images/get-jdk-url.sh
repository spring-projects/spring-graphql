#!/bin/bash
set -e

case "$1" in
  java17)
    echo "https://download.bell-sw.com/java/17.0.8.1+1/bellsoft-jdk17.0.8.1+1-linux-amd64.tar.gz"
  java19)
    echo "https://github.com/bell-sw/Liberica/releases/download/19.0.2+9/bellsoft-jdk19.0.2+9-linux-amd64.tar.gz"
    ;;
  *)
    echo $"Unknown java version"
    exit 1
esac
