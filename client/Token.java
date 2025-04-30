package client;

/**
 * Represents a token that authorizes the download of a limited number of file blocks from a helper.
 */
public class Token {
    private String tokenId;
    private int allowedBlocks;

    /**
     * Constructs a Token instance with a token ID and the number of allowed blocks.
     *
     * @param tokenId Unique identifier for the token
     * @param allowedBlocks Number of blocks the token allows to be downloaded
     */
    public Token(String tokenId, int allowedBlocks) {
        this.tokenId = tokenId;
        this.allowedBlocks = allowedBlocks;
    }

    /**
     * Returns the unique token ID.
     *
     * @return the token ID
     */
    public String getTokenId() {
        return tokenId;
    }

    /**
     * Returns the number of blocks that can be downloaded using this token.
     *
     * @return the allowed number of blocks
     */
    public int getAllowedBlocks() {
        return allowedBlocks;
    }
}
