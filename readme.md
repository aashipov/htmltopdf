### HTML to PDF ###

Receive a static HTML-page named ```index.html``` (and optional CSS, graphics, fonts etc) and produce PDF page(s) via ```wkhtmltopdf``` or ```chromium --headless ... --print-to-pdf=...```

#### Prior art ####

```https://www.chromium.org```

```https://github.com/wkhtmltopdf/wkhtmltopdf```

```https://github.com/thecodingmachine/gotenberg```

#### How-to ####

Runs on TCP port 8080

HTTP Endpoints:

```/``` or ```/health``` responds if program is alive

```/html``` converts via wkhtmltopdf (faster)

```/chromium``` converts via chromium (slower)

##### Docker #####

The preferred way - from Docker Hub ```docker run -d --rm --name=htmltopdf -p 8080:8080  aashipov/htmltopdf:latest```

OR

Local build & run ```bash build-and-run.bash```

##### On-premise #####

Install ```curl```, ```bash```, [patched ```wkhtmltopdf```](https://wkhtmltopdf.org/downloads.html), ```chromium / chrome.exe```, add to ```PATH```, Go compiler toolchain

```go build && ./htmltopdf```

##### Test #####

```cd temp && bash post.bash```

#### Why Go? ####

Modern C

#### Why Centos ####

Out of the box Chromium Headless, reasonable image size

#### License ####

Perl The "Artistic License"
