package net.crunkhouse.shaitzov;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;

import net.crunkhouse.shaitzov.cards.CardClickedListener;
import net.crunkhouse.shaitzov.cards.CardSource;
import net.crunkhouse.shaitzov.cards.GameRuleUtils;
import net.crunkhouse.shaitzov.cards.PileDirection;
import net.crunkhouse.shaitzov.cards.PlayingCard;
import net.crunkhouse.shaitzov.cards.PlayingCardUtils;
import net.crunkhouse.shaitzov.ui.CardSpacingDecorator;
import net.crunkhouse.shaitzov.ui.DeckOverlapDecorator;
import net.crunkhouse.shaitzov.ui.HandOverlapDecorator;
import net.crunkhouse.shaitzov.ui.PileOverlapDecorator;
import net.crunkhouse.shaitzov.ui.PlayingCardAdapter;
import net.crunkhouse.shaitzov.ui.SimpleItemTouchHelperCallback;
import net.crunkhouse.shaitzov.welcome.WelcomeActivity;

import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final int INITIAL_HAND_SIZE = 3;
    private static final int FACE_UP_AND_DOWN_CARD_AMOUNT = 3;

    private boolean godmode = false;

    private PlayingCardAdapter playerHandAdapter;
    private PlayingCardAdapter pileAdapter;
    private PlayingCardAdapter deckAdapter;
    private PlayingCardAdapter faceDownAdapter;
    private PlayingCardAdapter faceUpAdapter;
    private RecyclerView playerHandView;
    private RecyclerView pileView;
    private PileDirection currentDirection = PileDirection.UP;
    private LocalPreferences prefs;
    private CardClickedListener cardClickedListener = new CardClickedListener() {
        @Override
        public boolean onCardClicked(CardSource source, PlayingCard card) {
            boolean success = false;
            switch (source) {
                case HAND:
                    // Can only play from the hand if it's a valid card.
                    if (playCardSuccessful(card)) {
                        playerHandAdapter.remove(card);
                        // Now, if the user has less than 3 cards and there is still a deck,
                        // one of two options...remind them to draw, or auto-draw for them.
                        // Let's auto-draw for now.
                        // TODO: make this a setting?
                        if (deckAdapter.getItemCount() > 0 && playerHandAdapter.getItemCount() < 3) {
//                        snack("You should draw more cards!");
                            onCardClicked(CardSource.DECK, null);
                        }
                        success = true;
                    }
                    break;
                case DECK:
                    // If a deck card was clicked, we want to actually draw the top card as long as our hand is less than 3
                    if (godmode || playerHandAdapter.getItemCount() < 3) {
                        card = deckAdapter.getCards().get(deckAdapter.getItemCount() - 1);
                        deckAdapter.remove(card);
                        playerHandAdapter.add(card);
                        playerHandView.scrollToPosition(playerHandAdapter.getItemCount() - 1);
                        success = true;
                        snack("You drew a " + card.toString());
                    } else {
                        snack("You can't draw more cards, you have 3 or more.");
                    }
                    break;
                case FACE_UP:
                    // Only do something if all player-hand cards are gone
                    if (playerHandAdapter.getItemCount() == 0) {
                        if (playCardSuccessful(card)) {
                            faceUpAdapter.remove(card);
                            success = true;
                        }
                    } else {
                        // Tell user they can't play that card right now.
                        snack("You can't play a face-up card while you have a hand!");
                    }
                    break;
                case FACE_DOWN:
                    // Only do something if all face-up cards AND hand cards are gone
                    if (faceUpAdapter.getItemCount() == 0 && playerHandAdapter.getItemCount() == 0) {
                        if (playCardSuccessful(card)) {
                            faceDownAdapter.remove(card);
                            success = true;
                        } else {
                            // We still want to remove the card from face-down...
                            // but now it goes to the hand, along with the pile!
                            faceDownAdapter.remove(card);
                            playerHandAdapter.add(card);
                            onCardClicked(CardSource.PILE, null);
                        }
                    } else {
                        // Tell user they can't play that card right now.
                        snack("You can't play a face-down card while you have a hand or face-up cards!");
                    }
                    break;
                case PILE:
                    // take all cards from pile, put in hand
                    ArrayList<PlayingCard> cards = pileAdapter.getCards();
                    playerHandAdapter.addAll(cards);
                    playerHandView.scrollToPosition(playerHandAdapter.getItemCount() - 1);
                    pileAdapter.clear();
                    currentDirection = PileDirection.UP;
                    success = true;
                default:
                    break;
            }
            if (playerHandAdapter.getItemCount() == 0 &&
                    faceUpAdapter.getItemCount() == 0 &&
                    faceDownAdapter.getItemCount() == 0) {
                // This player is done with the game!!!
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogStyle);
                builder.setTitle("You won!");
                builder.setPositiveButton("New game", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FirebaseUtils.clearGameInBackground(prefs);
                        // TODO: let them watch the game until it's finished, once we have multiplayer
                        MainActivity.this.recreate();
                    }
                });
                builder.show();
                success = true;
            }
            return success;
        }

        @Override
        public void switchDirection() {
            if (currentDirection == PileDirection.DOWN) {
                currentDirection = PileDirection.UP;
            } else {
                currentDirection = PileDirection.DOWN;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = ((ShaitzovApplication) getApplication()).prefs;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name) + ": " +
                    prefs.getPlayerNickname());
        }

        // Instantiate cards
        final ArrayList<PlayingCard> playerHand = new ArrayList<>(INITIAL_HAND_SIZE);
        ArrayList<PlayingCard> playerFaceDown = new ArrayList<>(FACE_UP_AND_DOWN_CARD_AMOUNT);
        ArrayList<PlayingCard> playerFaceUp = new ArrayList<>(FACE_UP_AND_DOWN_CARD_AMOUNT);
        ArrayList<PlayingCard> pile = new ArrayList<>(0);

        // Set up face-down view
        RecyclerView faceDownView = findViewById(R.id.player_facedown);
        faceDownAdapter = new PlayingCardAdapter(playerFaceDown, CardSource.FACE_DOWN, cardClickedListener);
        faceDownView.setAdapter(faceDownAdapter);
        faceDownView.addItemDecoration(new CardSpacingDecorator(faceDownView.getContext()));
        faceDownView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Set up face-down view
        RecyclerView faceUpView = findViewById(R.id.player_faceup);
        faceUpAdapter = new PlayingCardAdapter(playerFaceUp, CardSource.FACE_UP, cardClickedListener);
        faceUpView.setAdapter(faceUpAdapter);
        faceUpView.addItemDecoration(new CardSpacingDecorator(faceUpView.getContext()));
        faceUpView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Set up player hand view
        playerHandView = findViewById(R.id.player_hand);
        playerHandAdapter = new PlayingCardAdapter(playerHand, CardSource.HAND, cardClickedListener);
        playerHandView.setAdapter(playerHandAdapter);
        playerHandView.addItemDecoration(new HandOverlapDecorator(playerHandView.getContext()));
        playerHandView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Set up deck view
        RecyclerView deckView = findViewById(R.id.deck);
        deckAdapter = new PlayingCardAdapter(new ArrayList<PlayingCard>(0), CardSource.DECK, cardClickedListener);
        deckView.setAdapter(deckAdapter);
        deckView.addItemDecoration(new DeckOverlapDecorator(deckView.getContext()));
        deckView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, true));

        // Set up pile view
        pileView = findViewById(R.id.pile);
        pileAdapter = new PlayingCardAdapter(pile, CardSource.PILE, cardClickedListener);
        pileView.setAdapter(pileAdapter);
        pileView.addItemDecoration(new PileOverlapDecorator(pileView.getContext()));
        pileView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Add swipe-to-play support
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SimpleItemTouchHelperCallback(faceDownAdapter));
        itemTouchHelper.attachToRecyclerView(faceDownView);
        itemTouchHelper = new ItemTouchHelper(new SimpleItemTouchHelperCallback(faceUpAdapter));
        itemTouchHelper.attachToRecyclerView(faceUpView);
        itemTouchHelper = new ItemTouchHelper(new SimpleItemTouchHelperCallback(playerHandAdapter));
        itemTouchHelper.attachToRecyclerView(playerHandView);

        // Then, check to see if there's a game in progress (if a deck exists in the database)
        FirebaseUtils.checkGameExists(new FirebaseUtils.ResultListener() {
            @Override
            public void onResult(boolean deckExists) {
                if (!deckExists) {
                    // Populate deck
                    ArrayList<PlayingCard> deck = PlayingCardUtils.makeDeck();
                    // Add player face-down cards
                    for (int i = 0; i < FACE_UP_AND_DOWN_CARD_AMOUNT; i++) {
                        faceDownAdapter.add(PlayingCardUtils.drawFrom(deck));
                    }
                    // Add player face-up cards
                    for (int i = 0; i < FACE_UP_AND_DOWN_CARD_AMOUNT; i++) {
                        faceUpAdapter.add(PlayingCardUtils.drawFrom(deck));
                    }
                    // Add player hand
                    for (int i = 0; i < INITIAL_HAND_SIZE; i++) {
                        playerHandAdapter.add(PlayingCardUtils.drawFrom(deck));
                    }
                    Collections.sort(playerHandAdapter.getCards());
                    for (PlayingCard card : deck) {
                        deckAdapter.add(card);
                    }
                    // Write the cards to the database
                    FirebaseUtils.putCards(
                            deck,
                            new ArrayList<PlayingCard>(),
                            playerHandAdapter.getCards(),
                            faceUpAdapter.getCards(),
                            faceDownAdapter.getCards(),
                            prefs);
                } else {
                    // There's a game in progress, so sync the cards with the remote game
                    FirebaseUtils.getRemoteCards(deckAdapter, pileAdapter, playerHandAdapter, faceUpAdapter, faceDownAdapter,
                            new FirebaseUtils.ResultListener() {
                                @Override
                                public void onResult(boolean playerWasDealt) {
                                    if (!playerWasDealt) {
                                        // If the player wasn't dealt in, then we have to grab cards from the deck
                                        ArrayList<PlayingCard> deck = deckAdapter.getCards();
                                        // Add player face-down cards
                                        for (int i = 0; i < FACE_UP_AND_DOWN_CARD_AMOUNT; i++) {
                                            faceDownAdapter.add(PlayingCardUtils.drawFrom(deck));
                                        }
                                        // Add player face-up cards
                                        for (int i = 0; i < FACE_UP_AND_DOWN_CARD_AMOUNT; i++) {
                                            faceUpAdapter.add(PlayingCardUtils.drawFrom(deck));
                                        }
                                        // Add player hand
                                        for (int i = 0; i < INITIAL_HAND_SIZE; i++) {
                                            playerHandAdapter.add(PlayingCardUtils.drawFrom(deck));
                                        }
                                        Collections.sort(playerHandAdapter.getCards());
                                        deckAdapter.notifyDataSetChanged();
                                        FirebaseUtils.putCards(
                                                deckAdapter.getCards(),
                                                pileAdapter.getCards(),
                                                playerHandAdapter.getCards(),
                                                faceUpAdapter.getCards(),
                                                faceDownAdapter.getCards(),
                                                prefs);
                                    }
                                }
                            }, prefs);
                }
            }
        }, prefs.getGameId());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        Switch godSwitch = menu.findItem(R.id.action_godmode).getActionView().findViewById(R.id.action_switch);
        godSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                godmode = isChecked;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_new_game:
                new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogStyle)
                        .setMessage(R.string.sure_to_exit)
                        .setPositiveButton(R.string.exit, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                finish();
                                startActivity(new Intent(MainActivity.this, WelcomeActivity.class));
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .show();
                return true;
//            case R.id.action_settings:
//                // TODO: settings view
//                return true;
            case R.id.action_licenses:
                new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogStyle)
                        .setTitle(R.string.licenses)
                        .setMessage(Html.fromHtml(getString(R.string.license_text)))
                        .show();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean playCardSuccessful(PlayingCard card) {
        if (godmode || GameRuleUtils.canPlayCardOnPile(card, pileAdapter.getCards(), currentDirection)) {
            pileAdapter.add(card);
            pileView.scrollToPosition(pileAdapter.getItemCount() - 1);

            // Resolve special effects for 10, 4x, 7, 8
            if (GameRuleUtils.shouldBurnPile(card, pileAdapter.getCards())) {
                pileAdapter.clear();
                currentDirection = PileDirection.UP;
            } else if (GameRuleUtils.shouldDirectionSwitch(card)) {
                if (currentDirection == PileDirection.DOWN) {
                    currentDirection = PileDirection.UP;
                } else {
                    currentDirection = PileDirection.DOWN;
                }
            } else if (GameRuleUtils.shouldDirectionReset(card)) {
                currentDirection = PileDirection.UP;
            } else if (GameRuleUtils.shouldSkipPlayer(card)) {
                // TODO: resolve special effects for 8 (doesn't matter for single player)
            }
            return true;
        } else {
            // Tell user they can't play that card right now.
            snack("You can't play a " + card.getValueName() + " on that pile");
            return false;
        }
    }

    private void snack(String text) {
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_SHORT).show();
    }
}
