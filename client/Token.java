package client;

public class Token {
    private String tokenId;
    private int allowedBlocks;

    public Token(String tokenId, int allowedBlocks) {
        this.tokenId = tokenId;
        this.allowedBlocks = allowedBlocks;
    }

    public String getTokenId() { return tokenId; }
    public int getAllowedBlocks() { return allowedBlocks; }
}
