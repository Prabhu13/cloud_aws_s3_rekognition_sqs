package com.example;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.core.SdkBytes;

import java.io.InputStream;
import java.util.List;

public class App {
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

            // List images in S3 bucket
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(BUCKET_NAME)
                    .build();
            ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);

            for (S3Object s3Object : response.contents()) {
                String imageName = s3Object.key();
                if (imageName.endsWith(".jpg") || imageName.endsWith(".png")) {
                    // Download the image from S3
                    byte[] imageBytes = downloadImageFromS3(s3Client, BUCKET_NAME, imageName);

                    // Detect car in the image using Rekognition
                    boolean isCar = detectCarInImage(rekognitionClient, imageBytes);

                    // Send the result to SQS only if a car is detected
                    if (isCar) {
                        sendResultToSQS(sqsClient, imageName);
                    }
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

    // Modify the detectCarInImage function to accept byte[] instead of String
    private static boolean detectCarInImage(RekognitionClient rekognitionClient, byte[] imageBytes) {
        // Convert byte[] to SdkBytes
        SdkBytes sdkBytes = SdkBytes.fromByteArray(imageBytes);

        // Build the Image object for Rekognition using bytes
        Image image = Image.builder()
                .bytes(sdkBytes)
                .build();

        // Create the request to detect labels in the image
        DetectLabelsRequest request = DetectLabelsRequest.builder()
                .image(image)
                .maxLabels(10)
                .minConfidence(90F)
                .build();

        // Call the detectLabels API with the request
        DetectLabelsResponse response = rekognitionClient.detectLabels(request);

        // Retrieve the list of labels detected in the image
        List<Label> labels = response.labels();

        // Iterate through the labels to check if "Car" is detected
        for (Label label : labels) {
            if (label.name().equalsIgnoreCase("Car") && label.confidence() >= 90F) {
                return true; // A car is detected with a confidence level of 90% or more
            }
        }

        // No car detected with sufficient confidence
        return false;
    }

    // Send only the image name if a car is detected
    private static void sendResultToSQS(SqsClient sqsClient, String imageName) {

        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(SQS_QUEUE_URL)
                .messageBody(imageName)
                .build();

        sqsClient.sendMessage(sendMsgRequest);
        System.out.println("Sent message to SQS: " + imageName);
    }
}
