package com.example;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.core.SdkBytes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class AppB {
    private static final String BUCKET_NAME = "njit-cs-643"; // Replace with your bucket name
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/927389155615/car_index"; // Replace with your SQS URL

    public static void main(String[] args) {
        Region region = Region.US_EAST_1; // Adjust the region if necessary

        try (S3Client s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
             RekognitionClient rekognitionClient = RekognitionClient.builder()
                     .region(region)
                     .credentialsProvider(DefaultCredentialsProvider.create())
                     .build();
             SqsClient sqsClient = SqsClient.builder()
                     .region(region)
                     .credentialsProvider(DefaultCredentialsProvider.create())
                     .build()) {

            // Receive messages from SQS
            while (true) { // Continuous polling loop
                ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(SQS_QUEUE_URL)
                        .maxNumberOfMessages(10) // Adjust the number as needed
                        .waitTimeSeconds(20) // Long polling for 20 seconds
                        .build();

                ReceiveMessageResponse receiveResponse = sqsClient.receiveMessage(receiveRequest);
                List<Message> messages = receiveResponse.messages();

                if (messages.isEmpty()) {
                    System.out.println("No messages in queue, waiting...");
                    continue;
                }

                for (Message message : messages) {
                    String imageName = message.body(); // SQS message now contains just the image name (e.g., "1.jpg")

                    // Download the image from S3
                    byte[] imageBytes = downloadImageFromS3(s3Client, BUCKET_NAME, imageName);

                    // Detect text in the image using Rekognition
                    String detectedText = detectTextInImage(rekognitionClient, imageBytes);

                    // Output the result (write to file or log)
                    outputDetectedText(imageName, detectedText);

                    // Delete the processed message from the SQS queue
                    deleteMessageFromQueue(sqsClient, message);
                }
            }
        }
    }

    // Function to download the image from S3 as a byte array
    private static byte[] downloadImageFromS3(S3Client s3Client, String bucketName, String imageName) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(imageName)
                .build();

        try (InputStream inputStream = s3Client.getObject(getObjectRequest)) {
            return inputStream.readAllBytes();  // Convert InputStream to byte array
        } catch (Exception e) {
            throw new RuntimeException("Failed to download image from S3", e);
        }
    }

    // Function to detect text in the image using Rekognition
    private static String detectTextInImage(RekognitionClient rekognitionClient, byte[] imageBytes) {
        // Convert byte[] to SdkBytes
        SdkBytes sdkBytes = SdkBytes.fromByteArray(imageBytes);

        // Build the Image object for Rekognition using bytes
        Image image = Image.builder()
                .bytes(sdkBytes)
                .build();

        // Create the request to detect text in the image
        DetectTextRequest request = DetectTextRequest.builder()
                .image(image)
                .build();

        // Call the detectText API with the request
        DetectTextResponse response = rekognitionClient.detectText(request);

        // Retrieve and concatenate detected text
        StringBuilder detectedText = new StringBuilder();
        for (TextDetection text : response.textDetections()) {
            detectedText.append(text.detectedText()).append("\n");
        }

        return detectedText.toString().trim();  // Return the detected text
    }

    // Function to output the detected text (could be logging, file writing, etc.)
    private static void outputDetectedText(String imageName, String detectedText) {
        try {
            String fileName = "detected_text_" + imageName + ".txt";
            Files.write(Paths.get(fileName), detectedText.getBytes());
            System.out.println("Detected text for " + imageName + " written to " + fileName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write detected text to file", e);
        }
    }

    // Function to delete a message from SQS after it has been processed
    private static void deleteMessageFromQueue(SqsClient sqsClient, Message message) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(SQS_QUEUE_URL)
                .receiptHandle(message.receiptHandle())
                .build();

        sqsClient.deleteMessage(deleteRequest);
        System.out.println("Deleted message from SQS: " + message.body());
    }
}