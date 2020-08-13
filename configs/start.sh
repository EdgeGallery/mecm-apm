#!/bin/bash

echo "Running APM"
cd /usr/app || exit
java -jar apm-0.0.1-SNAPSHOT.jar
