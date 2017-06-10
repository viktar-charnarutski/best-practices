package concurency;

/**
 * Outage API.
 *
 * @author Viktar Charnarutski
 */
public interface Outage {

    /**
     * Opens <em>outage</em>.
     */
    void openOutage();

    /**
     * Closes <em>outage</em>.
     */
    void closeOutage();

    /**
     * Requests <em>outage</em>.
     */
    void requestOutage();

    /**
     * Checks if the <em>outage</em> is in progress.
     */
    boolean isOutageInProgress();

}
