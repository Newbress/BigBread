package com.bigbread.api.review.photo;

/** 사진을 받아들일 수 없을 때. 메시지는 그대로 사용자에게 보여줘도 되는 문구다. */
public class PhotoRejectedException extends RuntimeException {

    public PhotoRejectedException(String message) {
        super(message);
    }
}
