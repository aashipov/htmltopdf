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

```/html``` converts via chromium (slower, produces zombies)

```/wkhtmltopdf``` converts via wkhtmltopdf (faster, won't produce zombies)

##### Docker #####

```bash build-and-run.bash```

The preferred way, chromium zombies in container

##### On-premise #####

Install ```curl```, ```bash```, [patched ```wkhtmltopdf```](https://wkhtmltopdf.org/downloads.html), ```chromium / chrome.exe```, add to ```$PATH / %PATH%```

##### Test #####

```cd temp && bash post.bash```

#### Why Go? ####

Modern C

#### License ####

Perl The "Artistic License"
