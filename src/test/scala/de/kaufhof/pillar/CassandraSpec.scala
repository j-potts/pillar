package de.kaufhof.pillar

import com.datastax.driver.core.Cluster
import com.typesafe.config.ConfigFactory
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.duration._

/**
  * A mixin for repo test specs that require a Cassandra instance for testing.
  *
  * Pillar Migration Files are required for table creation, and must be located under "conf/migrations/"
  *
  * When tests are run with this mixed in, it will attempt to start an embedded instance of Cassandra,
  * which will then be usable by the unit test code.
  *
  * The following must be added to build.sbt to pull in the test resources:
  * "com.weightwatchers.core"    %% "core-commons" % "x.x.x" % "test" classifier("tests")
  *
  * The following must be provided by the implementor:
  *
  * protocolVersion The protocol version, 3 is recommended for Cassandra 2.1.
  * keyspaceName The name of the keyspace.
  *
  * The database can then be instantiated as follows:
  * {{{object testCassandraProgramRepo extends CassandraProgramRepo with cassandraDatabase.keySpace.Connector}}}
  *
  */
trait CassandraSpec extends ScalaFutures with BeforeAndAfterAll {
  this: Suite =>

  //These must be lazy to ensure correct init order
  protected lazy val port = EmbeddedCassandraServerHelper.getNativeTransportPort

  lazy val cluster = {
    startEmbeddedCassandra()
    Cluster.builder().addContactPoint("127.0.0.1").withPort(port).build()
  }

  lazy val session = cluster.connect()

  protected def startEmbeddedCassandra(): Unit = try {
    //Start the Cassandra Instance
    println("Starting embedded Cassandra...")
    EmbeddedCassandraServerHelper.startEmbeddedCassandra("cassandra_embedded.yaml", 60.seconds.toMillis)
    println(s"Cassandra running on Port $port")
  } catch {
    case e: Exception =>
      System.err.println(s"Error starting Embedded Cassandra: $e")
  }

  override protected def afterAll(): Unit = {
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
    super.afterAll()
  }

}

