# Maven Application Plugin

generates an application file. An application file is a single executable file containing your entire Java application, it's all you need to distribute and run it. 
Technically, an application file is a shell script with an appended executable jar file (which contains all application classes and its dependencies). 
You can invoke it directly or you can pass it as an argument to java -jar. You can rename it without breaking the application.

## Prerequisites

* Java 7
* the application file works for Linux or Mac OS. Windows is not supported - contributions welcome!
* if you want to build the plugin: Maven 3

## Setup

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

to your pom.xml and run `mvn clean package` to generate the application file `target/yourapp`.

Note: to deploy the application into your Maven repository, run mvn deploy. The plugin attaches your application file and thus Maven will 
upload it into your repository. Please note that the deployed application file name is your projects artifact name with "-application.sh" 
appended, it's not the name you specified with the name parameter. If users download application files from the repository, they have to 
adjust the file name and make the file executable (e.g with `chmod`)

Note 2: you can use `jar tf yourapp` to list the files in the resulting application file.

[Generated documentation](http://mlhartme.github.io/maven-application-plugin/application/)


## Duplicate Files

The application file contains a jar file that's a merger of all transitve dependencies of type jar (runtime and compile) plus the classes 
in your project. What happens if two dependencies contain a file foo/bar.class? Without additional configuration, the plugin aborts with an
"duplicate file" error.

To fix this, you have to configure how to handle the duplicate file. You have four options:

  * overwrite: the file from the last dependency is added to the application file
  * equal: duplicate files are accepted if they have equal content; one copy is added to the application file
  * concat: duplicate files will be concatenated, the result is added to the application file
  * remove: none of the files will be added to the application file

For example, you can add the parameter

    <equal>foo/bar.class</equal>

to the plugin configuration. This will fix your build if all "foo/bar.class" files in your dependencies have equal content. The plugins will abort with an error if the files differ.

All parameters accept comma-separated paths with Ant-style wild cards. For example, 

    <remove>**/README*, **/readme*</remove>

would remove all readmes from the application file.


## Alternatives

Other plugins with similar functionality:

  * Maven's [Shade Plugin](http://maven.apache.org/plugins/maven-shade-plugin/plugin-info.html) can package all classes into a single jar file,
    possibly renaming duplicate classes
  * Maven's [Assembly Plugin](http://maven.apache.org/plugins/maven-assembly-plugin/) can create arbitrary archives, including jars.
