package com.ureca.news.controller;

import com.ureca.news.model.User;
import com.ureca.news.utils.JwtTokenUtil;
import com.ureca.news.repository.UserRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class GoogleLoginController {

    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;

    public GoogleLoginController(UserRepository userRepository, JwtTokenUtil jwtTokenUtil) {
        this.userRepository = userRepository;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @GetMapping("/login-success")
    public RedirectView loginSuccess(OAuth2AuthenticationToken authentication) {
        System.out.println("login-success");
        String googleId = authentication.getPrincipal().getAttribute("sub");
        String name = authentication.getPrincipal().getAttribute("name");
        String email = authentication.getPrincipal().getAttribute("email");

        User user = new User();
        user.setId(googleId);
        user.setName(name);
        user.setEmail(email);
        userRepository.save(user);

        String jwtToken = jwtTokenUtil.generateToken(email);

        RedirectView redirectView = new RedirectView();
        redirectView.setUrl("http://localhost:3000/" + jwtToken);
        return redirectView;
    }
}
