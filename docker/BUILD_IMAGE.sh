#!/bin/bash

( cd ..; mvn clean package  )
cp ../target/l9g-oidc-info.jar .
docker build -t l9g-oidc-info:latest .
