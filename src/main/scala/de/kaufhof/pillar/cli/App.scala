package de.kaufhof.pillar.cli

import java.io.File
import java.util.Map.Entry

import com.datastax.driver.core.Cluster
import com.typesafe.config.{ConfigFactory, ConfigObject, ConfigValue}
import de.kaufhof.pillar._
import de.kaufhof.pillar.config.ConnectionConfiguration

import scala.util.{Failure, Success, Try}

object App {
  def apply(reporter: Reporter = new PrintStreamReporter(System.out)): App = {
    new App(reporter)
  }

  def main(arguments: Array[String]) {
    try {
      App().run(arguments)
    } catch {
      case exception: Exception =>
        System.err.println(exception.getMessage)
        System.exit(1)
    }

    System.exit(0)
  }
}

class App(reporter: Reporter) {
  private val configuration = ConfigFactory.load()

  def run(arguments: Array[String]) {
    val commandLineConfiguration = CommandLineConfiguration.buildFromArguments(arguments)
    val registry = Registry.fromDirectory(new File(commandLineConfiguration.migrationsDirectory, commandLineConfiguration.dataStore), reporter)
    val dataStoreName = commandLineConfiguration.dataStore
    val environment = commandLineConfiguration.environment

    val cassandraConfiguration = new ConnectionConfiguration(dataStoreName, environment, configuration)

    val cluster:Cluster = createCluster(cassandraConfiguration)

    val session = commandLineConfiguration.command match {
      case Initialize => cluster.connect()
      case _ => cluster.connect(cassandraConfiguration.keyspace)
    }

    val replicationOptions = try {
      getReplicationStrategy(dataStoreName, environment)
    } catch {
      case e: Exception => throw e
    }

    // TODO: Command shouldn't be the sole point of entry when passing things into a migration.
    // TODO: This should be refactored at some point.
    val command = Command(
      commandLineConfiguration.command,
      session,
      cassandraConfiguration.keyspace,
      commandLineConfiguration.timeStampOption,
      registry,
      replicationOptions)

    try {
      CommandExecutor().execute(command, reporter)
    } finally {
      session.close()
    }
  }

  private def createCluster(connectionConfiguration:ConnectionConfiguration): Cluster = {
    val clusterBuilder = Cluster.builder()
      .addContactPoint(connectionConfiguration.seedAddress)
      .withPort(connectionConfiguration.port)
    connectionConfiguration.auth.foreach(clusterBuilder.withAuthProvider)

    connectionConfiguration.sslConfig.foreach(_.setAsSystemProperties())
    if (connectionConfiguration.useSsl)
      clusterBuilder.withSSL()

    clusterBuilder.build()
  }

  private final case class ReplicationStrategyConfigError(msg: String) extends Exception

  /**
    * Parses replication settings from a config that looks like:
    * {{{
    *   replicationStrategy: "SimpleStrategy"
    *   replicationFactor: 3
    * }}}
    *
    * or:
    *
    * {{{
    *   replicationStrategy: "NetworkTopologyStrategy"
    *   replicationFactor: [
    *     {dc1: 3},
    *     {dc2: 3}
    *   ]
    * }}}
    *
    * @param dataStoreName The target data store, as defined in application.conf
    * @param environment The environment, as defined in application.conf (i.e. "pillar.dataStoreName.environment {...})
    * @return ReplicationOptions with a default of Simple Strategy with a replication factor of 3.
    */
  private def getReplicationStrategy(dataStoreName: String, environment: String): ReplicationStrategy = try {
    val repStrategyStr = Try(configuration.getString(s"pillar.$dataStoreName.$environment.replicationStrategy"))

    repStrategyStr match {
      case Success(repStrategy) => repStrategy match {
        case "SimpleStrategy" =>
          val repFactor = configuration.getInt(s"pillar.$dataStoreName.$environment.replicationFactor")
          SimpleStrategy(repFactor)

        case "NetworkTopologyStrategy" =>
          import scala.collection.JavaConverters._
          val dcConfigBuffer = configuration
            .getObjectList(s"pillar.$dataStoreName.$environment.replicationFactor")
            .asScala

          val dcBuffer = for {
            item: ConfigObject <- dcConfigBuffer
            entry: Entry[String, ConfigValue] <- item.entrySet().asScala
            dcName = entry.getKey
            dcRepFactor = entry.getValue.unwrapped().toString.toInt
          } yield (dcName, dcRepFactor)

          val datacenters = dcBuffer
            .map(dc => CassandraDataCenter(dc._1, dc._2))
            .toList

          NetworkTopologyStrategy(datacenters)

        case _ =>
          throw new ReplicationStrategyConfigError(s"$repStrategy is not a valid replication strategy.")
      }

      case Failure(e) => SimpleStrategy()
    }
  } catch {
    case e: Exception => throw e
  }
}
