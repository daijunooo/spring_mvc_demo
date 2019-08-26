package com.mvc.demo;

import com.mvc.framework.annotation.Service;

@Service
public class DemoImpl implements DemoService {
    @Override
    public String echo() {
        return "Hello Spring";
    }
}
