package com.mvc.demo;

import com.mvc.framework.annotation.Autowired;
import com.mvc.framework.annotation.Controller;
import com.mvc.framework.annotation.RequestMapping;

@Controller
public class DemoController {

    @Autowired
    private DemoService service;

    @RequestMapping("/hello")
    public String echo() {
        return service.echo();
    }
}
