package com.bigbread.api.user;

import com.bigbread.api.auth.AppUserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 즐겨찾기. 로그인이 필요하다(비로그인은 401). 변경 요청은 CSRF 토큰이 필요하다. */
@RestController
@RequestMapping("/api/my/favorites")
public class FavoriteController {

    private final FavoriteService favorites;

    public FavoriteController(FavoriteService favorites) {
        this.favorites = favorites;
    }

    @GetMapping
    public List<FavoriteService.Item> list(@AuthenticationPrincipal AppUserPrincipal me) {
        return favorites.list(me.userId());
    }

    @PutMapping("/{bakeryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long bakeryId) {
        favorites.add(me.userId(), bakeryId);
    }

    @DeleteMapping("/{bakeryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long bakeryId) {
        favorites.remove(me.userId(), bakeryId);
    }
}
