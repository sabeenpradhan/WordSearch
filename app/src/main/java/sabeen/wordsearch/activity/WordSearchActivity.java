package sabeen.wordsearch.activity;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import sabeen.wordsearch.GetChallengeAsyncTask;
import sabeen.wordsearch.R;

/**
 * Created by sabeen on 7/21/16.
 * Main Activity for Word Search(For parsing, loading and displaying JSON data and handling Touch)
 */

public class WordSearchActivity extends AppCompatActivity {
    private static final String CHALLENGE_URL = "http://s3.amazonaws.com/duolingo-data/s3/js2/find_challenges.txt";
    private SharedPreferences preferences;
    private GetChallengeAsyncTask getChallengeAsyncTask;

    private GridView gridView;
    private ArrayAdapter<String> characterArrayAdapter;
    private TextView sourceTextView;

    private Integer puzzleNumber = 0;
    private Integer firstPosition = 0;
    private Integer position;
    private Integer columnNumber;
    private String sourceWord;

    private List<String> puzzleCharacterList;
    private List<String> targetWordLocationCoordinatesList;
    private TreeSet<Integer> targetPostionSet;
    private Set<TreeSet<Integer>> targetPostionTreeSets;
    private Set<TreeSet<Integer>> solvedTargetTreeSets;
    private TreeSet<Integer> traversedPositonSet;
    private Dialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.about_me);

        gridView = (GridView) findViewById(R.id.characterGridView);
        preferences = this.getSharedPreferences("Challenge", MODE_PRIVATE);
        sourceTextView = (TextView) findViewById(R.id.sourceTV);

//      Calling Async Task only for the first time after the app is installed
        if (preferences.getBoolean("firstTime", true)) {
            getChallengeAsyncTask = new GetChallengeAsyncTask(this);
            getChallengeAsyncTask.execute(CHALLENGE_URL);
            try {
//       Waiting for Aync Task to get completed
                getChallengeAsyncTask.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        loadData();
        handleTouch();

    }

    /**
     * Method to parse, load and display JSON obtained from the API
     */
    public void loadData() {
//      Checking if puzzleNumber is last
        if (puzzleNumber == preferences.getInt("maxPuzzle", 1)) {
            restartGame();
        }
//      Chaning the puzzle by one
        ++puzzleNumber;

        puzzleCharacterList = new ArrayList<>();
        traversedPositonSet = new TreeSet<>();
        targetPostionTreeSets = new HashSet<>();
        solvedTargetTreeSets = new HashSet<>();
        targetWordLocationCoordinatesList = new ArrayList<>();

        try {
//          Getting JSONObject from Shared Preference
            JSONObject topJsonObject = new JSONObject(preferences.getString("JSONObject" + puzzleNumber, null));
            sourceWord = topJsonObject.getString("word").toUpperCase();
            sourceTextView.setText(sourceWord);

            JSONObject wordLocation = topJsonObject.getJSONObject("word_locations");
//          Getting Wordlocation Keys that is soluting coordinate list
            Iterator<String> keys = wordLocation.keys();
            while (keys.hasNext()) {
                targetWordLocationCoordinatesList.add(keys.next());
            }
//          Getting Puzzle characters
            JSONArray characterGridArray = topJsonObject.getJSONArray("character_grid");
            columnNumber = characterGridArray.length();
            for (int i = 0; i < columnNumber; i++) {
                JSONArray characters = characterGridArray.getJSONArray(i);
                for (int j = 0; j < characters.length(); j++) {
                    puzzleCharacterList.add(characters.getString(j));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        addTargetPositions();
        characterArrayAdapter = new ArrayAdapter<String>(this,
                R.layout.character_items, puzzleCharacterList);
        gridView.setNumColumns(columnNumber);
        gridView.setAdapter(characterArrayAdapter);
    }


    /**
     * Method to handle users touch event and process for correctness
     */
    private void handleTouch() {
        gridView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float currentXPosition = event.getX();
                float currentYPosition = event.getY();
//              Position from gridview where user hand is placed
                position = gridView.pointToPosition((int) currentXPosition, (int) currentYPosition);
                int maskedAction = event.getActionMasked();
                schiffe_loop:
                switch (maskedAction) {
                    case MotionEvent.ACTION_DOWN:
                        if (position >= 0) {
                            firstPosition = position;
                            processPosition();
                        } else {
                            clearSelectionAndTraversedSet();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        if (position > 0) {
                            processPosition();
                        } else {
                            clearSelectionAndTraversedSet();
                        }
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                        if (position >= 0) {
                            for (TreeSet<Integer> targetPositionSet : targetPostionTreeSets) {
//                              Checking if User's traversed position equals any of the target Position
                                if (targetPositionSet.equals(traversedPositonSet)) {
                                    solvedTargetTreeSets.add(targetPositionSet);
                                    traversedPositonSet.clear();
//                                    Check if all the target are Solved
                                    if (targetPostionTreeSets.equals(solvedTargetTreeSets)) {
                                        Toast.makeText(WordSearchActivity.this, "Correct", Toast.LENGTH_SHORT).show();
                                        loadData();
                                    } else {
                                        Toast.makeText(WordSearchActivity.this, "Correct, Please find the next one", Toast.LENGTH_SHORT).show();
                                    }
                                    break schiffe_loop;
                                }
                            }
                            clearSelectionAndTraversedSet();
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    /**
     * Method to convert Target Coordinates into grid position and adding it to Set
     */
    public void addTargetPositions() {
        List<List<String>> coordinateDoubleList = new ArrayList<>();
//      Changing coordinate to array
        for (String s : targetWordLocationCoordinatesList) {
            coordinateDoubleList.add(Arrays.asList(s.split(",")));
        }
        for (List<String> s : coordinateDoubleList) {
            Integer a = 0;
            Integer b = 1;
            targetPostionSet = new TreeSet<>();
            for (int i = 0; i < s.size() / 2; i++) {
                Integer firstElement = Integer.parseInt(s.get(a));
                Integer secondElement = Integer.parseInt(s.get(b));
//              (K*y)+x to convert target coordiniates(x,y) to grid position
                Integer mergedElement = firstElement + (secondElement * columnNumber);
                a += 2;
                b += 2;
                targetPostionSet.add(mergedElement);
            }
            targetPostionTreeSets.add(targetPostionSet);
        }
    }

    /**
     * Method to check if user is swiping right or bottom or diagonal
     */
    public void processPosition() {
        clearSelectionAndTraversedSet();
        if (isRight()) {
            reDrawRight();
        } else if (isDiagonal()) {
            reDrawBottom(columnNumber + 1);
        } else if (isBottom()) {
            reDrawBottom(columnNumber);
        }
    }

    /**
     * Method to check if user traversed right
     *
     * @return true if user traversed right
     */
    public boolean isRight() {
        return (position - firstPosition) > 0 && (position - firstPosition) <= (getUpperBoundRight() - firstPosition);
    }

    /**
     * Method to check if user traversed bottom right diagonal
     *
     * @return true if user traversed bottom right diagonal
     */
    public boolean isDiagonal() {
        return (position - firstPosition) % (columnNumber + 1) == 0 && (position - firstPosition) >= 0;
    }

    /**
     * Method to check if user traversed bottom
     *
     * @return true if user traversed bottom
     */
    public boolean isBottom() {
        return (position - firstPosition) % columnNumber == 0 && (position - firstPosition) >= 0;
    }

    /**
     * Method to clear highlighted Selection and elements from Traversed Set
     */
    public void clearSelectionAndTraversedSet() {
        for (Integer pos : traversedPositonSet) {
//          If puzzle contains two target words
            if (!solvedTargetTreeSets.isEmpty()) {
                for (TreeSet<Integer> integers : solvedTargetTreeSets) {
                    if (!integers.contains(pos)) {
                        clearHighlight(pos);
                    }
                }
            } else {
                clearHighlight(pos);
            }
        }
        traversedPositonSet.clear();
    }

    /**
     * Method to give upper bound of the grid row where first touch was made
     *
     * @return max Integer of the grid row
     */
    public Integer getUpperBoundRight() {
        return (((firstPosition / columnNumber) + 1) * columnNumber) - 1;
    }

    /**
     * Method to add traversed positon and highlight selection for Bottom and Diagonal
     *
     * @param divider to check if it is diagonal or bottom
     */
    public void reDrawBottom(Integer divider) {
        for (int i = firstPosition; i <= position; i++) {
            if ((i - firstPosition) % (divider) == 0) {
                traversedPositonSet.add(i);
                highlightSelection(i);
            }
        }
    }

    /**
     * Method to add traversed postion and highlight selection for right
     */
    public void reDrawRight() {
        for (int i = firstPosition; i <= position; i++) {
            if ((i - firstPosition) >= 0 && (i - firstPosition) <= (getUpperBoundRight() - firstPosition)) {
                traversedPositonSet.add(i);
                highlightSelection(i);
            }
        }
    }

    /**
     * Method to highlight Selected gridview items based on direction
     *
     * @param position gridview items
     */
    public void highlightSelection(Integer position) {
        TextView textView = (TextView) gridView.getChildAt(position);
        textView.setBackgroundResource(R.drawable.textview_border_selected);
    }

    /**
     * Method to clear highlight from gridview items
     *
     * @param position gridview items
     */
    public void clearHighlight(Integer position) {
        TextView textView = (TextView) gridView.getChildAt(position);
        textView.setBackgroundResource(R.drawable.textview_border);
    }

    /**
     * Show alert dialogue if all the puzzle are solved
     */
    public void restartGame() {
        puzzleNumber = 0;
        new AlertDialog.Builder(this)
                .setTitle("Congratulations")
                .setMessage("Level 1 completed, higher levels coming soon!")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.word_search_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//       Handle item selection
        switch (item.getItemId()) {
            case R.id.about:
                dialog.show();
                ImageView close = (ImageView) dialog.findViewById(R.id.close);
                close.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}

