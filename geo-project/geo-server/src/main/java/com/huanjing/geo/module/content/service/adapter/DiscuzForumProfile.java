package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
public class DiscuzForumProfile {
    private String baseUrl;
    private String loginPageUrl;
    private String loginSubmitUrl;
    private String postPageUrl;
    private String postSubmitUrl;
    private Integer fid = 317;
    private Integer connectTimeoutMs = 5000;
    private Integer requestTimeoutMs = 30000;
    private Boolean rememberLogin = true;
    private String successUrlRegex = "(thread|forum)\\-\\d+";
    private List<Board> boards = List.of();

    public URI baseUri() {
        String value = StringUtils.hasText(baseUrl) ? baseUrl : "https://www.right.com.cn/forum/";
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return URI.create(value);
    }

    public URI loginPageUri() {
        return resolve(StringUtils.hasText(loginPageUrl) ? loginPageUrl : "forum.php?mod=post&action=newthread&fid=" + fid);
    }

    public URI loginSubmitUri() {
        return resolve(StringUtils.hasText(loginSubmitUrl)
                ? loginSubmitUrl
                : "member.php?mod=logging&action=login&loginsubmit=yes&infloat=yes&lssubmit=yes");
    }

    public URI postPageUri() {
        return resolve(materializeFid(StringUtils.hasText(postPageUrl) ? postPageUrl : "forum.php?mod=post&action=newthread&fid=" + fid));
    }

    public URI postSubmitUri() {
        return resolve(materializeFid(StringUtils.hasText(postSubmitUrl)
                ? postSubmitUrl
                : "forum.php?mod=post&action=newthread&fid=" + fid + "&extra=&topicsubmit=yes"));
    }

    private URI resolve(String value) {
        URI uri = URI.create(value);
        return uri.isAbsolute() ? uri : baseUri().resolve(uri);
    }

    private String materializeFid(String value) {
        if (fid == null || !StringUtils.hasText(value)) {
            return value;
        }
        return value.replace("fid=?", "fid=" + fid)
                .replace("{fid}", String.valueOf(fid));
    }

    public boolean hasBoards() {
        return boards != null && !boards.isEmpty();
    }

    public Optional<Board> resolveBoard(Integer requestedFid) {
        if (!hasBoards()) {
            Integer effectiveFid = requestedFid == null ? fid : requestedFid;
            return effectiveFid == null
                    ? Optional.empty()
                    : Optional.of(new Board(effectiveFid, null, true, false));
        }
        if (requestedFid != null) {
            return boards.stream()
                    .filter(Objects::nonNull)
                    .filter(Board::isEnabled)
                    .filter(board -> requestedFid.equals(board.getFid()))
                    .findFirst();
        }
        return boards.stream()
                .filter(Objects::nonNull)
                .filter(Board::isEnabled)
                .min(Comparator.comparing((Board board) -> !Boolean.TRUE.equals(board.getDefaultBoard()))
                        .thenComparing(board -> board.getFid() == null ? Integer.MAX_VALUE : board.getFid()));
    }

    public DiscuzForumProfile withFid(Integer nextFid) {
        DiscuzForumProfile copy = new DiscuzForumProfile();
        copy.setBaseUrl(baseUrl);
        copy.setLoginPageUrl(loginPageUrl);
        copy.setLoginSubmitUrl(loginSubmitUrl);
        copy.setPostPageUrl(postPageUrl);
        copy.setPostSubmitUrl(postSubmitUrl);
        copy.setFid(nextFid);
        copy.setConnectTimeoutMs(connectTimeoutMs);
        copy.setRequestTimeoutMs(requestTimeoutMs);
        copy.setRememberLogin(rememberLogin);
        copy.setSuccessUrlRegex(successUrlRegex);
        copy.setBoards(boards);
        return copy;
    }

    @Data
    public static class Board {
        private Integer fid;
        private String name;
        private Boolean enabled = true;
        @JsonProperty("default")
        private Boolean defaultBoard = false;

        public Board() {
        }

        public Board(Integer fid, String name, Boolean enabled, Boolean defaultBoard) {
            this.fid = fid;
            this.name = name;
            this.enabled = enabled;
            this.defaultBoard = defaultBoard;
        }

        public boolean isEnabled() {
            return !Boolean.FALSE.equals(enabled);
        }
    }
}
