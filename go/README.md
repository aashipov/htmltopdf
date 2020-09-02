### HTML to PDF ###

Receive a static HTML-page named ```index.html``` (and optional CSS, graphics, fonts etc) and produce PDF page(s) via ```wkhtmltopdf``` or ```chromium``` (via cdp or chromedp)

#### Prior art ####

```https://github.com/wkhtmltopdf/wkhtmltopdf```

```https://github.com/mafredri/cdp```

```https://www.chromium.org```

```https://github.com/thecodingmachine/gotenberg```

#### How-to ####

Runs on TCP port 8080

HTTP Endpoints:

```/``` or ```/health``` responds if program is alive

```/html``` converts via wkhtmltopdf (faster)

```/chromium``` converts via chromium (slower)

Paper size, margins & orientation ```/html/a3/landscape/top50/right30/bottom50/left30``` or ```/htmla3landscapetop50right30bottom50left30``` prints on landscape oriented A3 canvas

Chromium Default instrumentation - via cdp, environment variable ```CHROMIUM_HARNESS=chromedp``` switches to chromedp

##### Docker #####

The preferred way - from Docker Hub ```docker run -d --name=htmltopdf -p 8080:8080 aashipov/htmltopdf:centos-cdp``` or ```docker run -d --name=htmltopdf -p 8080:8080 aashipov/htmltopdf:debian-cdp```

##### On-premise #####

Install ```curl```, ```bash```, [patched ```wkhtmltopdf```](https://wkhtmltopdf.org/downloads.html), [```chromium```](https://www.chromium.org/getting-involved/download-chromium), Go compiler toolchain, add to ```PATH```

```go build && bash entrypoint.bash```

##### Performance #####

If conversion via ```chromium``` is a must, consider multiple containers, otherwise use ```wkhtmltopdf```

```maxDevtConnections``` constant limits the number of parallel DevTools connections, larger values cause Chromium to bloat
