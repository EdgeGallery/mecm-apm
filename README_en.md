# mecm-apm

#### Description
Application package manager responsible for application package management including Onboarding, distributing package to edge etc.

#### Compile and build
The APM project is containerized based on docker, and it is divided into two steps during compilation and construction.

#### Compile
APM is a Java program written based on jdk1.8 and maven. To compile, you only need to execute mvn install to compile and generate jar package

#### Build image
The APM project provides a dockerfile file for mirroring. You can use the following commands when making a mirror

docker build -t edgegallery/mecm-apm:latest -f docker/Dockerfile .