# Maven Application Plugin

generates an application file. An application file is a single file containing your entire Java application, it's all you need to distribute and run it. 
Technically, an application file is a shell script with an appended executable jar file (which contains all application classes and its dependencies). 
You can invoke it directly or you can pass it as an argument to java -jar. You can rename it without breaking the application.

## Prerequisites

* Java 7
* the application file works for Linux or Mac OS. Windows is not supported - contributions welcome!
* if you want to build the plugin: Maven 3

## Setup:

Add

    <plugin>
      <groupId>net.oneandone.maven.plugins</groupId>
      <artifactId>application</artifactId>
      <version>1.6.2</version>
      <configuration>
        <name>yourapp</name>
        <main>foo.yourapp.Main</main>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
    </plugin>

to your pom.xml and run mvn clean package to generate the application file target/yourapp.

Note: to deploy the application into your Maven repository, run mvn deploy. The plugin attaches your application file and thus Maven will 
upload it into your repository. Please note that the deployed application file name is your projects artifact name with "-application.sh" 
appended, it's not the name you specified with the name parameter.

Note 2: you can use zip -l to list the files in the resulting application file; jar tf (as of jdk 1.6.0_24) fails.

[Generated documentation](http://mlhartme.github.io/maven-application-plugin/application/)


## Duplicate Files

The application file contains a jar file that's a merger of all transitve dependencies of type jar (runtime and compile) plus the classes 
in your project. What happens if two dependencies contain a file foo/bar.class? Without additional configuration, the plugin reports an
"duplicate file" error.

To fix the build, you have to configure how to deal with the duplicate file. You have four options:

  * overwrite: the file from the last dependency is added to the application file
  * equal: duplicate files are accepted if they have equal content
  * concat: duplicate files will be concatenated
  * remove: none of the files will be added to the application file

For example, you can add the parameter

    <equal>foo/bar.class</equal>

to the plugin configuration. This will fix your build if all "foo/bar.class" files in your dependencies have equal content. The plugins will still report a failure if the files differ.

All parameters accept comma-separated paths with Ant-style wild cards. For example, 

    <overwrite>**/*</overwrite>

would silence all "duplicate file" errors reported by the plugin.


## Alternatives

Other plugins provide functionality to the application plugin:

  * Maven's Shade Plugin can package all classes into a single jar file, but it cannot add the Launcher Script to this jar. You always end up with your application in two files.
  * Same with Maven's Assembly Plugin.
