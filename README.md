# mecm-apm

#### 描述
mecm-apm负责应用包管理，包括包管理，包上传、将包分发到边缘等功能。

#### 编译和构建
APM项目基于docker容器化，在编译和构建过程中分为两个步骤。

#### 编译
APM是基于jdk1.8和maven编写的Java程序。 编译只需执行 mvn install 即可编译生成jar包

#### 构建镜像
APM 项目提供了一个用于镜像的 dockerfile 文件。 制作镜像时可以使用以下命令

docker build -t edgegallery/mecm-apm:latest -f docker/Dockerfile .

#### 文档资料
更多内容，请点击[链接](http://docs.edgegallery.org/zh_CN/latest/Projects/MECM/MECM%2Ehtml)查阅详细内容。