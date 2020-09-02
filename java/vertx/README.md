### HTML to PDF ###

https://github.com/aashipov/htmltopdf twin, implemented in JavaSE (Vert.x)

Expects chromium executable at /usr/bin/chromium. Use chromium.executable system property to override

##### Local Build and Run #####

```mvn clean compile exec:java```

##### Docker, single instance #####

```docker pull aashipov/htmltopdf-javase:vertx && docker run -d --rm --name=htmltopdf-javase -p 8080:8080 aashipov/htmltopdf-javase:vertx```
