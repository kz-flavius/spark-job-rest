package com.job

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{S3ObjectInputStream, GetObjectRequest, ObjectListing, ListObjectsRequest}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FSDataOutputStream, Path, FileSystem}
import org.apache.hadoop.io.IOUtils
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import scala.util.Try

/**
 * Created by raduchilom on 4/18/15.
 */
object S3Utils {

  val log = LoggerFactory.getLogger(getClass)

  def getFiles(bucketName: String, accessKey: String, secretAccessKey: String):List[(Int, String)] = {

    val s3Client: AmazonS3Client = getS3Client(accessKey, secretAccessKey)

    val fileList = ListBuffer[(Int, String)]()

    try {
      log.info("Listing objects from S3")
      var counter = 0

      val listObjectsRequest = new ListObjectsRequest()
        .withBucketName(bucketName)
       var objectListing: ObjectListing = null

      do {
        import scala.collection.JavaConversions._
        objectListing = s3Client.listObjects(listObjectsRequest)
        objectListing.getObjectSummaries.foreach { objectSummary =>
          if(!objectSummary.getKey.endsWith(Path.SEPARATOR)) {
            fileList += Tuple2(counter, objectSummary.getKey)
            counter += 1
          }
        }
        listObjectsRequest.setMarker(objectListing.getNextMarker());
      } while (objectListing.isTruncated())

      log.info("Finished listing objects from S3")

    } catch {
      case e: Exception => {
        log.error("Failed listing files. ", e)
        throw e
      }
    }

    fileList.toList
  }

  def getS3Client(accessKey: String, secretAccessKey: String): AmazonS3Client = {
    val awsCreds = new BasicAWSCredentials(accessKey, secretAccessKey)
    val s3client = new AmazonS3Client(awsCreds)
    s3client
  }

  def downloadFile(bucketName: String, key: String, outputFolder: String, s3Client: AmazonS3Client): (Try[Any], String) = {

    val downloadTry = Try {

      var inputStream: S3ObjectInputStream = null
      var outputStream: FSDataOutputStream = null

      try {

        log.info(s"Downloading file: $key")
        val s3object = s3Client.getObject(new GetObjectRequest(bucketName, key))
        inputStream = s3object.getObjectContent

        val outputPath = outputFolder + Path.SEPARATOR + key
        log.info(s"Writing file to: $outputPath")

        val conf = new Configuration();
        conf.set("fs.defaultFS", outputFolder)
        // new instance & set file in configuration
        val fs = FileSystem.get(conf);
        outputStream = fs.create(new Path(outputPath));

        IOUtils.copyBytes(inputStream, outputStream, 8192)

        fs.getFileStatus(new Path(outputPath)).getLen

      } finally {
        if(inputStream != null) {
          inputStream.close()
        }
        if(outputStream != null) {
          outputStream.close()
        }
      }
    }

    (downloadTry, key)
  }

}
