package br.com.contabilizape.centraldesk.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
@RequestMapping("/users")
public class UserController {

    // @PostMapping("/register")
    // public String returnInfo(){
    //     return "Não há usuários";

    // }
    @GetMapping("/register")
    public String returnInfo(){
        return "Não há usuários";

    }
}
