### HTML to PDF ###

Receive a static HTML-page named ```index.html``` (and optional CSS, graphics, fonts etc) and produce PDF page(s) via ```wkhtmltopdf``` or ```chromium --headless ... --print-to-pdf=...```

#### Prior art ####

```https://www.chromium.org```

```https://github.com/wkhtmltopdf/wkhtmltopdf```

```https://github.com/thecodingmachine/gotenberg```

#### How-to ####

App runs on TCP port 8080 by default

Endpoints:

```/``` or ```/health``` responds if program is alive

```/html``` converts via chromium

```/wkhtmltopdf``` converts via wkhtmltopdf (faster)

##### Docker #####

```bash build-and-run.bash```

##### On-premise #####

Install ```curl```, ```bash```, [patched ```wkhtmltopdf```](https://wkhtmltopdf.org/downloads.html) and ```chromium / chrome.exe```, add to $PATH / %PATH%

##### Test #####

```cd temp && bash post.bash```

#### Why Go? ####

Modern C

#### License ####

Perl The "Artistic License"
