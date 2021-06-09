# mecm-apm

#### 描述
应用包管理器负责应用包管理，包括入职、将包分发到边缘等。

#### 编译和构建
APM项目基于docker容器化，在编译和构建过程中分为两个步骤。

#### 编译
APM是基于jdk1.8和maven编写的Java程序。 编译只需执行 mvn install 即可编译生成jar包

#### 构建镜像
APM 项目提供了一个用于镜像的 dockerfile 文件。 制作镜像时可以使用以下命令

docker build -t edgegallery/mecm-apm:latest -f docker/Dockerfile 。