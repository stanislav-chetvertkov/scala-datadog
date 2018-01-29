package com.github.chet

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import org.scalatest.FreeSpecLike
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

class DatadogClientSpec extends TestKit(ActorSystem()) with FreeSpecLike with ScalaFutures{

  override implicit val patienceConfig: PatienceConfig = super.patienceConfig.copy(timeout = 10.seconds, interval = 100.milliseconds)

  import system.dispatcher
  implicit val mt = ActorMaterializer()

  "should query monitor" in {
    val apiKey="key"
    val appKey="key2"

    val id = 1728875

    val client = DatadogClient(apiKey, appKey)
    val r = client.getMonitor(id).value.futureValue

    print(r)
  }

}
