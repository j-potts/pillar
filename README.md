# Pillar

[![Gitter](https://badges.gitter.im/Galeria-Kaufhof/pillar.svg)](https://gitter.im/Galeria-Kaufhof/pillar?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge) [![License](http://img.shields.io/:license-mit-blue.svg)](http://doge.mit-license.org)
[![Build Status](https://travis-ci.org/Galeria-Kaufhof/pillar.svg?branch=master)](https://travis-ci.org/Galeria-Kaufhof/pillar)

Pillar manages migrations for your [Cassandra][cassandra] data stores.

[cassandra]:http://cassandra.apache.org

Pillar grew from a desire to automatically manage Cassandra schema as code. Managing schema as code enables automated
build and deployment, a foundational practice for an organization striving to achieve [Continuous Delivery][cd].

Pillar is to Cassandra what [Rails ActiveRecord][ar] migrations or [Play Evolutions][evolutions] are to relational
databases with one key difference: Pillar is completely independent from any application development framework.

[cd]:http://en.wikipedia.org/wiki/Continuous_delivery
[ar]:https://github.com/rails/rails/tree/master/activerecord
[evolutions]:http://www.playframework.com/documentation/2.0/Evolutions

## Installation

### Prerequisites

1. Java SE 6 runtime environment
1. Cassandra 2.0 with the native CQL protocol enabled

### From Source

This method requires [Simple Build Tool (sbt)][sbt].
Building an RPM also requires [Effing Package Management (fpm)][fpm].

    % sbt assembly   # builds just the jar file in the target/ directory

    % sbt rh-package # builds the jar and the RPM in the target/ directory
    % sudo rpm -i target/pillar-1.0.0-DEV.noarch.rpm

The RPM installs Pillar to /opt/pillar.

[sbt]:http://www.scala-sbt.org
[fpm]:https://github.com/jordansissel/fpm

### Packages

Pillar is available at Maven Central under the GroupId de.kaufhof and ArtifactId pillar_2.10 or pillar_2.11. The current version is 3.1.0.

#### sbt

  libraryDependencies += "de.kaufhof" %% "pillar" % "3.1.0"

#### Gradle

  compile 'de.kaufhof:pillar_2.10:3.0.0'
  compile 'de.kaufhof:pillar_2.11:3.0.0'

## Usage

### Terminology

Data Store
: A logical grouping of environments. You will likely have one data store per application.

Environment
: A context or grouping of settings for a single data store. You will likely have at least development and production
environments for each data store.

Migration
: A single change to a data store. Migrations have a description and a time stamp indicating the time at which it was
authored. Migrations are applied in ascending order and reversed in descending order.

### Command Line

#####Here's the short version:

Given the configuration:

```
pillar.my_keyspace {
  prod {
     ...
  }
  development {
       ...
    }
}
```
  1. Write migrations, place them in conf/pillar/migrations/myapp.
  1. Add pillar settings to conf/application.conf.
  1. `% pillar initialize -e prod my_keyspace`
  1. `% pillar migrate -e prod my_keyspace`

*Note: development is the default environment if nothing is specified*

Or we could compile and run the jar:

```
java -cp "slf4j-simple.jar:pillar-assembly.jar" de.kaufhof.pillar.cli.App -d "path/to/migrations" -e "prod" initialize "my_keyspace"
```

#### Migration Files

Migration files contain metadata about the migration, a [CQL][cql] statement used to apply the migration and,
optionally, a [CQL][cql] statement used to reverse the migration. Each file describes one migration. You probably
want to name your files according to time stamp and description, 1370028263_creates_views_table.cql, for example.
Pillar reads and parses all files in the migrations directory, regardless of file name.

[cql]:http://cassandra.apache.org/doc/cql3/CQL.html

Pillar supports reversible, irreversible and reversible with a no-op down statement migrations. Here are examples of
each:

Reversible migrations have up and down properties.

    -- description: creates views table
    -- authoredAt: 1370028263
    -- up:

    CREATE TABLE views (
      id uuid PRIMARY KEY,
      url text,
      person_id int,
      viewed_at timestamp
    )

    -- down:

    DROP TABLE views

Irreversible migrations have an up property but no down property.

    -- description: creates events table
    -- authoredAt: 1370023262
    -- up:

    CREATE TABLE events (
      batch_id text,
      occurred_at uuid,
      event_type text,
      payload blob,
      PRIMARY KEY (batch_id, occurred_at, event_type)
    )

Reversible migrations with no-op down statements have an up property and an empty down property.

    -- description: adds user_agent to views table
    -- authoredAt: 1370028264
    -- up:

    ALTER TABLE views
    ADD user_agent text

    -- down:

The Pillar command line interface expects to find migrations in conf/pillar/migrations unless overriden by the
-d command-line option.

#### Configuration

Pillar uses the [Typesafe Config][typesafeconfig] library for configuration. The Pillar command-line interface expects
to find an application.conf file in ./conf or ./src/main/resources.
The ReplicationStrategy and ReplicationFactor can be configured per environment. If left out completely,
SimplyStrategy with RF 3 will be used by default.
Given a data store called faker, the application.conf might look like the following:

```
    pillar.faker {
        development {
            cassandra-seed-address: "127.0.0.1"
            cassandra-keyspace-name: "pillar_development"
            replicationStrategy: "SimpleStrategy"
            replicationFactor: 0
        }
    }
```
```
    pillar.faker {
        development {
            cassandra-seed-address: "127.0.0.1"
            cassandra-keyspace-name: "pillar_development"
            replicationStrategy: "NetworkTopologyStrategy"
            replicationFactor: [
                {dc1: 2},
                {dc2: 3}
            ]
        }
    }
```

##### SSL & Authentication
You can optionally add ssl options and authentication to each of the environments:

    pillar.faker {
        development {
            cassandra-seed-address: "127.0.0.1"
            cassandra-keyspace-name: "pillar_development"
            auth {
                username: cassandra
                password: secret
            }
        }
        test {
            auth {
                username: cassandra
                password: secret
            }
            use-ssl: true
            ssl-options: {
                    # ssl with just a trust store for test environment
                    trust-store-path: foobar.jks # maps to javax.net.ssl.trustStore
                    trust-store-password: secret # maps to javax.net.ssl.trustStorePassword
                    trust-store-type: JKS        # maps to javax.net.ssl.trustStoreType
                }

            }
        }
        production {
            auth {
                username: cassandra
                password: secret
            }
            use-ssl: true
            ssl-options {
                trust-store-path: foobar.jks # maps to javax.net.ssl.trustStore
                trust-store-password: secret # maps to javax.net.ssl.trustStorePassword
                trust-store-type: JKS        # maps to javax.net.ssl.trustStoreType
                key-store-path: keystore.jks # maps to javax.net.ssl.keyStore
                key-store-password: secret   # maps to javax.net.ssl.keyStorePassword
                key-store-type: JKS          # maps to javax.net.ssl.keyStoreType
            }

    }

[typesafeconfig]:https://github.com/typesafehub/config

Reference the acceptance spec suite for details.

#### The pillar Executable

The package installs to /opt/pillar by default. The /opt/pillar/bin/pillar executable usage looks like this:

    Usage: pillar [OPTIONS] command data-store

    OPTIONS

    -d directory
    --migrations-directory directory  The directory containing migrations

    -e env
    --environment env                 environment

    -t time
    --time-stamp time                 The migration time stamp

    PARAMETERS

    command     migrate or initialize

    data-store  The target data store, as defined in application.conf

#### More Examples

Initialize the faker datastore development environment

    % pillar -e development initialize faker

Apply all migrations to the faker datastore development environment

    % pillar -e development migrate faker

### Library

You can also integrate Pillar directly into your application as a library.
Reference the acceptance spec suite for details.

### Release Notes

#### 1.0.1

* Add a "destroy" method to drop a keyspace (iamsteveholmes)

#### 1.0.3

* Clarify documentation (pvenable)
* Update Datastax Cassandra driver to version 2.0.2 (magro)
* Update Scala to version 2.10.4 (magro)
* Add cross-compilation to Scala version 2.11.1 (magro)
* Shutdown cluster in migrate & initialize (magro)
* Transition support from StreamSend to Chris O'Meara (comeara)

#### 2.0.0

* Allow configuration of Cassandra port (fkoehler)
* Rework Migrator interface to allow passing a Session object when integrating Pillar as a library (magro, comeara)

#### 2.0.1

* Update a argot dependency to version 1.0.3 (magro)

### 2.1.0

* Update to Cassandra dependency to version 3.0.0 (MarcoPriebe)

### 2.1.1

* Update to sbt-sonatype dependency to version 1.1 (MarcoPriebe)
* Update to Scala to version 2.11.6 (MarcoPriebe)

### 3.0.0

* change package structure to de.kaufhof (MarcoPriebe)

### 3.1.0

* Allow authentication and ssl connections (convoi)
* Small bugfixes
