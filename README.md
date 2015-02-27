Sweetie
=======

Small Java Web Framework for big projects.


## Installation

First of all whe need to download and import Sweetie Framework in your IDE, create new maven project and add dependence in `pom.xml`:

```
<dependency>
  <groupId>${project.groupId}</groupId>
  <artifactId>sweetie</artifactId>
  <version>${project.version}</version>
</dependency>
```

## Using

Every web application must contain at least main class, one controller and routes map.

Let's create simple cotroller thar returns `Hello world!` for every client:

```
package com.example.controllers;

import com.showvars.sweetie.foundation.Context;
import com.showvars.sweetie.foundation.Controller;
import com.showvars.sweetie.foundation.Response;

public class HelloWorldController extends Controller {

    public static Response index(Context ctx) throws Exception {
        return ok("Hello world!");
    }

}
```

Now we need to add this controller to routes map. Create file `helloworld.routesmap` in `resources/routes/` and add this:

```
GET     /   com.example.controllers.HelloWorldController.index()
```


Create main class:

```
package com.example;

import com.showvars.sweetie.SweetieApp;
import java.io.InputStreamReader;

public class HelloWorldApp {

    public static void main(String[] args) throws Exception {
        SweetieApp ss = SweetieApp.prepare();
        ss.getRouter().load(new InputStreamReader(HelloWorldApp.class.getResourceAsStream("/routes/helloworld.routesmap")));
        ss.getStarted();
    }

}
```

Compile and run. After that open link `http://localhost:8080/` in your browser where you can see Hello world!.
