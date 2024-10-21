# AWS Image Recognition Pipeline

This project is a distributed image recognition pipeline using AWS services like EC2, S3, SQS, and Rekognition. The system consists of two EC2 instances working in parallel: 
- EC2 A detects cars in images from an S3 bucket and sends the image index to SQS if a car is detected.
- EC2 B fetches the image index from SQS, downloads the image from S3, and detects text within the image.

## Prerequisites

1. **AWS Account**: Ensure you have an AWS account with access to EC2, S3, SQS, and Rekognition.
2. **AWS Credentials**: Your EC2 instances must have IAM role permissions to access S3, SQS, and Rekognition.
3. **Amazon Linux 2 AMI**: Both EC2 instances should be launched with Amazon Linux 2 AMI.
4. **Maven**: Install Maven to manage dependencies and compile the Java code.

## Step 1: Set Up AWS Services

### 1.1 Create an S3 Bucket

1. Create or use the existing bucket `njit-cs-643` to store images.
2. Upload sample images (JPG or PNG format) to the bucket.

### 1.2 Create an SQS Queue

1. Create a **standard SQS queue** named `car_index` in the AWS Console.
2. Note down the **Queue URL**.
3. Ensure the SQS queue is located in the **US East 1** region.

---

## Step 2: Set Up EC2 Instances

### 2.1 Create EC2 Instances

1. Launch **two EC2 instances** with **Amazon Linux 2 AMI**.
2. Use the **t2.micro** instance type (free-tier eligible).
3. Configure SSH access using the same `.pem` key for both instances.
4. Ensure both instances are in the **US East 1** region.

### 2.2 Configure Security Group

1. Allow the following inbound rules:
   - **SSH (port 22)** for remote access.
   - **HTTP (port 80)** and **HTTPS (port 443)**.
2. Restrict access to only your IP for security.

### 2.3 Connect to EC2 Instances

Connect to both EC2 instances using SSH:

```
ssh -i <your-key.pem> ec2-user@<your-ec2-public-ip>
```
### Step 3: Install Java and Maven on Both EC2 Instances
sudo yum install -y java-22-amazon-corretto-devel
sudo yum install maven -y

Initialize maven project
```
mvn archetype:generate -DgroupId=com.example -DartifactId=rekognition-s3-sqs -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```
### Step 4: EC2 A - Car Detection Run
```mvn clean test exec:java```

### Step 5: EC2 A - Car Detection Run

```mvn clean test exec:java```

