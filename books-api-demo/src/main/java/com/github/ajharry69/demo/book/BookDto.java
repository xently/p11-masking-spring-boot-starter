package com.github.ajharry69.demo.book;

import com.github.ajharry69.log.mask.Mask;

public record BookDto(String title, String author, @Mask String email, String phoneNumber) {
}
