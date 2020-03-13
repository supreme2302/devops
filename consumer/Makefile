
all: clean build docker

clean:
	gradle clean

build:
	gradle build

docker:
	docker build -t demo/consumer-service:1 .

deploy:
	helm install consumer-service devops/chart/consumer-service/

upgrade:
	helm upgrade consumer-service devops/chart/consumer-service/

remove:
	helm delete consumer-service
