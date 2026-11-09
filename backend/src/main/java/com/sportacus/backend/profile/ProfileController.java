package com.sportacus.backend.profile;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.form.FormSpec;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileService.AccountView account(@AuthenticationPrincipal User user) {
        return profiles.account(user);
    }

    @GetMapping("/form")
    public FormSpec form(@AuthenticationPrincipal User user) {
        return profiles.form(user);
    }

    @PostMapping("/form")
    public ProfileService.AccountView saveForm(@AuthenticationPrincipal User user, @RequestBody Map<String, Object> answers) {
        return profiles.saveForm(user, new Answers(answers));
    }

    @PatchMapping
    public ProfileService.AccountView patch(@AuthenticationPrincipal User user,
            @RequestBody ProfileService.PatchRequest body) {
        return profiles.patch(user, body);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal User user, @RequestBody ProfileService.PasswordRequest body) {
        profiles.changePassword(user, body);
    }

    @GetMapping("/export")
    public Map<String, Object> export(@AuthenticationPrincipal User user) {
        return profiles.export(user);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user) {
        profiles.delete(user);
    }
}
