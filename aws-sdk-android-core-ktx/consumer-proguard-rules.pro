-keep class * extends com.amazonaws.AmazonServiceException {
   *;
}
-keep class * extends com.amazonaws.AmazonWebServiceClient
-keep class * implements com.amazonaws.auth.Signer
-keep class * extends com.amazonaws.handlers.RequestHandler
-keep class * extends com.amazonaws.handlers.RequestHandler2
-keep class * extends com.amazonaws.metrics.MetricCollector
-keep class * implements com.amazonaws.metrics.MetricCollector$Factory
