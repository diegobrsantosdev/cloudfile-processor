# CloudFile Processor

CloudFile Processor is a **scalable, cloud-native system** that allows users to **upload files** and process them **asynchronously and reliably**. 

It leverages a **distributed architecture**: user files are stored in Amazon S3, processing tasks are queued in Amazon SQS, and metadata is tracked in Amazon DynamoDB. Backend services run on **Java + Spring Boot**, are **containerized with Docker**, and deployed on Amazon ECS. 

All infrastructure is managed with **CloudFormation**, ensuring scalability, reliability, and easy reproducibility.
