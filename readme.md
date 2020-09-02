### HTML to PDF ###

Convert a static HTML-page named ```index.html``` (and optional CSS, graphics, fonts etc) to PDF page(s) via ```wkhtmltopdf``` or ```chromium```

#### How-to ####

Runs on TCP port 8080

HTTP Endpoints:

Any URL but ```html``` or ```chromium``` responds if program is alive

```/html``` converts via wkhtmltopdf (faster)

```/chromium``` converts via chromium (slower)

Paper size, margins & orientation ```/html/a3/landscape/top50/right30/bottom50/left30``` or ```/htmla3landscapetop50right30bottom50left30``` prints on landscape oriented A3 canvas

##### Flavors #####

go (chromium instrumentation via cdp or chromedp)

java (undertow, jetty, vert.x or pure JavaSE as HTTP Server, chromium instrumentation via jvppeteer or chrome-devtools-kotlin)

ecmascript (chromium instrumentation via puppeteer or playwright)

##### Docker #####

The preferred way - from Docker Hub ```docker pull aashipov/htmltopdf:centos-go && docker run -d --name=htmltopdf -p 8080:8080 aashipov/htmltopdf:centos-go```

##### Test #####

```cd testing && bash post.bash```

##### Performance #####

If conversion via ```chromium``` is a must (e.g. SVG support or advanced CSS), consider multiple containers (see testing/farm), otherwise use ```wkhtmltopdf``` or include ```wkhtmltopdf``` into monolith app bundle
