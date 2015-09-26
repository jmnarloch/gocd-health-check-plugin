# Go Continuous Delivery Health Check plugin

> Go Continuous Delivery task for awaiting until your application is healthy

[![Build Status](https://travis-ci.org/jmnarloch/gocd-health-check-plugin.svg)](https://travis-ci.org/jmnarloch/gocd-health-check-plugin)

## Installation

Download the plugin and copy it into `/$GO_SERVER_HOME/plugins/external` and restart the Go server.

The plugin should appear on Plugins page.

## Usage

Add Health Check task to your build stage.

![Health Check task](screen.png)

## Options

### Url

The url to the the application health status. (required)

Example: http://localhost:8080/health

### Attribute

The name of attribute indicating the application status. (required)

Example: status

### Expected status

The application expected health status. (required)

Example: UP

### Delay

Time in seconds after a retry check will be performed. (required)

Example: 15

### Timeout

Maximum number of seconds to wait for the application to become healthy. (required)

Example: 60

## License

Apache 2.0