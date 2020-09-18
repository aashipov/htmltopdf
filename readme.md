### HTML to PDF ###

Receive a static HTML-page named ```index.html``` (and optional CSS, graphics, fonts etc) and produce PDF page(s) via ```wkhtmltopdf``` or ```chromium```

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

Paper size & orientation

```/chromium/a3/landscape``` prints on landscape oriented A3 canvas

##### Docker #####

The preferred way - from Docker Hub ```docker run -d --rm --name=htmltopdf -p 8080:8080  aashipov/htmltopdf:latest```

OR

Local build & run ```bash build-and-run.bash```

##### On-premise #####

Install ```curl```, ```bash```, [patched ```wkhtmltopdf```](https://wkhtmltopdf.org/downloads.html), ```chromium / chrome.exe```, Go compiler toolchain, add to ```PATH```

```go build && ./htmltopdf```

##### Test #####

```cd temp && bash post.bash```

##### Performance #####

If conversion via ```chromium``` is a must, consider multiple containers (see ```test/farm/farm-refresh.bash```), otherwise use ```wkhtmltopdf```

#### Why Go? ####

Modern C

#### Why Centos ####

Out of the box Chromium Headless
