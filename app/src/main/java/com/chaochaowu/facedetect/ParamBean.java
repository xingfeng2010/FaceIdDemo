package com.chaochaowu.facedetect;

public class ParamBean {
    public long msg_id;
    public ImageBean[] images;
    public int client_type;
    public String device_id;
    public String distance_threshold;

    public static class ImageBean {
        public int image_type;
        public String image_url;
        public String image_base64;
    }
}
