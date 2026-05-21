package com.goosepl.coastCalculator.web;

import com.goosepl.coastCalculator.domain.comment.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/recipes/{id}/comments")
    public String create(@PathVariable Long id,
                         @RequestParam(value = "content") String content,
                         @RequestParam(value = "parentId", required = false) Long parentId,
                         Principal principal) {
        commentService.create(id, content, parentId, principal.getName());
        return "redirect:/recipes/" + id + "#comments";
    }

    @PostMapping("/recipes/{rid}/comments/{cid}/delete")
    public String delete(@PathVariable Long rid,
                         @PathVariable Long cid,
                         Principal principal) {
        commentService.delete(cid, principal.getName());
        return "redirect:/recipes/" + rid + "#comments";
    }
}
