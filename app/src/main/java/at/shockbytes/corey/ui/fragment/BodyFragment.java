package at.shockbytes.corey.ui.fragment;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

import at.shockbytes.corey.R;
import at.shockbytes.corey.adapter.GoalAdapter;
import at.shockbytes.corey.body.info.BodyInfo;
import at.shockbytes.corey.body.BodyManager;
import at.shockbytes.corey.body.goal.Goal;
import at.shockbytes.corey.body.info.WeightPoint;
import at.shockbytes.corey.common.core.util.CoreyUtils;
import at.shockbytes.corey.common.core.util.view.CoreyViewManager;
import at.shockbytes.corey.dagger.AppComponent;
import at.shockbytes.corey.storage.live.LiveBodyUpdateListener;
import at.shockbytes.corey.ui.fragment.dialogs.AddGoalDialogFragment;
import at.shockbytes.util.AppUtils;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.functions.Consumer;

public class BodyFragment extends BaseFragment implements Palette.PaletteAsyncListener, Callback,
        LiveBodyUpdateListener, AddGoalDialogFragment.OnGoalMessagedAddedListener,
        GoalAdapter.OnGoalActionClickedListener {

    // -------------------------- Views --------------------------
    @BindView(R.id.fragment_body_pb_weight)
    protected ProgressBar progressBarWeight;

    @BindView(R.id.fragment_body_img_avatar)
    protected ImageView imgAvatar;

    @BindView(R.id.fragment_body_txt_weight)
    protected TextView txtWeight;

    @BindView(R.id.fragment_body_txt_bmi)
    protected TextView txtBMI;

    @BindView(R.id.fragment_body_txt_name)
    protected TextView txtName;

    @BindView(R.id.fragment_body_header)
    protected RelativeLayout headerLayout;

    @BindView(R.id.fragment_body_txt_dream_weight)
    protected TextView txtDreamWeight;

    @BindView(R.id.fragment_body_card_weight_graph)
    protected CardView cardViewWeight;

    @BindView(R.id.fragment_body_card_dream_weight)
    protected CardView cardViewDreamWeight;

    @BindView(R.id.fragment_body_card_goals)
    protected CardView cardViewGoals;

    @BindView(R.id.fragment_body_card_weight_graph_linechart)
    protected LineChart lineChartWeight;

    @BindView(R.id.fragment_body_card_dream_weight_txt_headline)
    protected TextView txtCardDreamWeightHeadline;

    @BindView(R.id.fragment_body_card_dream_weight_txt_content)
    protected TextView txtCardDreamWeightContent;

    @BindView(R.id.fragment_body_nestedscrollview)
    protected NestedScrollView nestedScrollView;

    @BindView(R.id.fragment_body_card_goals_rv)
    protected RecyclerView recyclerViewGoals;

    // -----------------------------------------------------------

    @Inject
    protected BodyManager bodyManager;

    private String weightUnit;

    private BodyInfo bodyInfo;
    private FirebaseUser user;

    private GoalAdapter goalAdapter;

    public static BodyFragment newInstance() {
        return new BodyFragment();
    }

    public BodyFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = FirebaseAuth.getInstance().getCurrentUser();
        weightUnit = bodyManager.getWeightUnit();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bodyManager.getBodyInfo().subscribe(new Consumer<BodyInfo>() {
            @Override
            public void accept(BodyInfo bodyInfo) {
                BodyFragment.this.bodyInfo = bodyInfo;
                setupViews();
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                Log.wtf("Corey", "BodyFragment: " + throwable.toString());
                throwable.printStackTrace();
            }
        });
    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_body;
    }

    @Override
    protected void injectToGraph(@NotNull AppComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        bodyManager.unregisterLiveBodyUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onGenerated(Palette palette) {

        int defaultColor = ContextCompat.getColor(getContext(), R.color.colorPrimary);
        int headerColor = palette.getDarkMutedColor(defaultColor);
        if (headerLayout != null) {
            CoreyViewManager.INSTANCE.backgroundColorTransition(headerLayout, defaultColor, headerColor);
        }
    }

    @Override
    public void onSuccess() {

        if (imgAvatar != null) {
            Bitmap bm = ((BitmapDrawable) imgAvatar.getDrawable()).getBitmap();
            if (bm != null) {
                imgAvatar.setImageDrawable(AppUtils.INSTANCE.createRoundedBitmap(getActivity(), bm));
                Palette.from(bm).generate(this);
            }
        }
    }

    @Override
    public void onError() {
        imgAvatar.setImageResource(R.drawable.ic_user);
    }

    @Override
    public void onDesiredWeightChanged(int changed) {
        bodyInfo.setDreamWeight(changed);
        setupUserCard();
        animateViews(true);
    }

    @Override
    public void onBodyGoalAdded(Goal g) {

        if (!g.isDone()) {
            goalAdapter.addEntityAtFirst(g);
        } else {
            goalAdapter.addEntityAtLast(g);
        }
    }

    @Override
    public void onBodyGoalDeleted(Goal g) {
        goalAdapter.deleteEntity(g);
    }

    @Override
    public void onBodyGoalChanged(Goal g) {
        goalAdapter.updateEntity(g);
    }

    @Override
    public void onDeleteGoalClicked(Goal g) {
        bodyManager.removeBodyGoal(g);
    }

    @Override
    public void onFinishGoalClicked(Goal g) {
        g.setDone(true);
        bodyManager.updateBodyGoal(g);
    }

    @Override
    public void onGoalMessageAdded(String goalMsg) {
        bodyManager.storeBodyGoal(new Goal(goalMsg, false, ""));
    }

    @OnClick(R.id.fragment_body_card_goals_btn_add)
    protected void onClickAddGoal() {

        AddGoalDialogFragment fragment = AddGoalDialogFragment.newInstance();
        fragment.setOnGoalMessageAddedListener(this);
        fragment.show(getFragmentManager(), fragment.getTag());
    }

    @Override
    public void setupViews() {

        if (bodyInfo == null) {
            return;
        }

        setupUserCard();
        setupGoalCard();
        setupDesiredWeightCard();
        setupWeightCard();

        animateViews(true);

        bodyManager.registerLiveBodyUpdates(this);
    }

    private void setupDesiredWeightCard() {

        String[] titles = getResources().getStringArray(R.array.dreamweight_motivation);
        String title = titles[new Random().nextInt(titles.length - 1)];
        txtCardDreamWeightHeadline.setText(title);

        double diff = AppUtils.INSTANCE.roundDouble(bodyInfo.getLatestWeightPoint().getWeight()
                - bodyInfo.getDreamWeight(), 1);
        String content = getString(R.string.dreamweight_card_text,
                diff + weightUnit,
                bodyInfo.getDreamWeight() + weightUnit);

        int idx_s1 = content.indexOf(" ");
        int idx_e1 = content.indexOf(" ", idx_s1 + 1);
        int idx_s2 = content.lastIndexOf(" ");
        SpannableString spanContent = new SpannableString(content);
        spanContent.setSpan(new StyleSpan(Typeface.BOLD), idx_s1, idx_e1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        spanContent.setSpan(new StyleSpan(Typeface.BOLD), idx_s2, spanContent.length() - 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        txtCardDreamWeightContent.setText(spanContent);
    }

    private void setupWeightCard() {

        // Style chart
        lineChartWeight.setDrawGridBackground(false);
        lineChartWeight.getAxisRight().setEnabled(false);
        lineChartWeight.getLegend().setEnabled(false);
        lineChartWeight.setDescription(null);
        lineChartWeight.setClickable(false);
        lineChartWeight.getAxisLeft().setDrawAxisLine(false);
        lineChartWeight.getAxisLeft().setDrawGridLines(false);
        lineChartWeight.getXAxis().setDrawGridLines(false);
        lineChartWeight.getXAxis().setDrawAxisLine(false);
        lineChartWeight.getAxisLeft().setTextColor(Color.WHITE);
        lineChartWeight.getXAxis().setTextColor(Color.WHITE);

        // Set data
        List<Entry> entries = new ArrayList<>();
        List<WeightPoint> weightPoints = bodyInfo.getWeightPoints();
        final String[] labels = new String[weightPoints.size()];
        for (int i = 0; i < weightPoints.size(); i++) {
            WeightPoint wp = weightPoints.get(i);
            labels[i] = CoreyUtils.INSTANCE.formatDate(wp.getTimeStamp(), true);
            entries.add(new Entry(i, (float) wp.getWeight()));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Weight");
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setColor(Color.WHITE);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);

        lineChartWeight.getXAxis().setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return labels[(int) value];
            }
        });
        lineChartWeight.getAxisLeft().setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return (int) value + " " + weightUnit;
            }
        });
        LineData lineData = new LineData(dataSet);
        lineChartWeight.setData(lineData);
        lineChartWeight.invalidate();
    }

    private void setupGoalCard() {

        recyclerViewGoals.setLayoutManager(new LinearLayoutManager(getContext()));
        goalAdapter = new GoalAdapter(getContext(), new ArrayList<Goal>());
        goalAdapter.setOnGoalActionClickedListener(this);
        recyclerViewGoals.setAdapter(goalAdapter);

        bodyManager.getBodyGoals().subscribe(new Consumer<List<Goal>>() {
            @Override
            public void accept(List<Goal> goals) {
                for (Goal g : goals) {
                    onBodyGoalAdded(g);
                }
            }
        });
    }

    private void setupUserCard() {

        String weight = bodyInfo.getLatestWeightPoint().getWeight() + " " + weightUnit;
        txtWeight.setText(weight);
        String bmi = "BMI: " + bodyInfo.getLatestBmi();
        txtBMI.setText(bmi);
        String dreamWeight = bodyInfo.getDreamWeight() + weightUnit;
        txtDreamWeight.setText(dreamWeight);
        txtName.setText(user.getDisplayName());

        Uri uri = user.getPhotoUrl();
        if (uri != null) {
            Picasso.with(getContext()).load(uri)
                    //.placeholder(R.drawable.ic_user)
                    .into(imgAvatar, this);
        }
    }

    private void animateViews(boolean animateCards) {

        int weightProgress = CoreyUtils.INSTANCE.calculateDreamWeightProgress(
                bodyInfo.getHighestWeight(),
                bodyInfo.getLatestWeightPoint().getWeight(),
                bodyInfo.getDreamWeight());

        //Animate image
        ObjectAnimator img_anim_alpha = ObjectAnimator.ofFloat(imgAvatar, "alpha", 0f, 1f);
        ObjectAnimator img_anim_scaleX = ObjectAnimator.ofFloat(imgAvatar, "scaleX", 0.7f, 1f);
        ObjectAnimator img_anim_scaleY = ObjectAnimator.ofFloat(imgAvatar, "scaleY", 0.7f, 1f);
        AnimatorSet imageSet = new AnimatorSet();
        imageSet.play(img_anim_alpha).with(img_anim_scaleX).with(img_anim_scaleY);
        imageSet.setDuration(500);
        imageSet.setInterpolator(new DecelerateInterpolator());
        imageSet.setStartDelay(200);
        imageSet.start();

        //Animate secondary weight progress
        ObjectAnimator secondaryWeightAnimation = ObjectAnimator.ofInt(progressBarWeight, "secondaryProgress", 100);
        secondaryWeightAnimation.setStartDelay(500);
        secondaryWeightAnimation.setDuration(750);
        secondaryWeightAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        secondaryWeightAnimation.start();

        //Animate weight progress
        ObjectAnimator weightAnimation = ObjectAnimator.ofInt(progressBarWeight, "progress", weightProgress);
        weightAnimation.setStartDelay(200);
        weightAnimation.setDuration(1500);
        weightAnimation.setInterpolator(new AnticipateOvershootInterpolator(1.0f, 4.0f));
        weightAnimation.start();

        //Add all textviews to ArrayList, we're going to animate them later
        ArrayList<View> animatedTextViews = new ArrayList<>();
        animatedTextViews.add(txtName);
        animatedTextViews.add(txtWeight);
        animatedTextViews.add(txtBMI);
        animatedTextViews.add(txtDreamWeight);

        //Animate all ListViews
        int TEXT_START_DELAY = 150;
        for (int i = 0; i < animatedTextViews.size(); i++) {

            //Animate weight txtExercise
            ObjectAnimator txt_anim_alpha = ObjectAnimator.ofFloat(animatedTextViews.get(i), "alpha", 0.3f, 1f);
            ObjectAnimator txt_anim_scaleX = ObjectAnimator.ofFloat(animatedTextViews.get(i), "scaleX", 0.3f, 1f);
            ObjectAnimator txt_anim_scaleY = ObjectAnimator.ofFloat(animatedTextViews.get(i), "scaleY", 0.3f, 1f);
            AnimatorSet txtSet = new AnimatorSet();
            txtSet.play(txt_anim_alpha).with(txt_anim_scaleX).with(txt_anim_scaleY);
            txtSet.setDuration(150);
            txtSet.setStartDelay(TEXT_START_DELAY * (i + 1));
            txtSet.setInterpolator(new DecelerateInterpolator());
            txtSet.start();
        }

        if (animateCards) {
            ArrayList<View> animatedCardViews = new ArrayList<>();
            animatedCardViews.add(cardViewDreamWeight);
            animatedCardViews.add(cardViewWeight);
            animatedCardViews.add(cardViewGoals);

            int CARD_START_DELAY = 200;
            for (int i = 0; i < animatedCardViews.size(); i++) {
                //Animate weight txtExercise
                ObjectAnimator card_anim_alpha = ObjectAnimator.ofFloat(animatedCardViews.get(i), "alpha", 0.1f, 1f);
                ObjectAnimator card_anim_scaleX = ObjectAnimator.ofFloat(animatedCardViews.get(i), "scaleX", 0.1f, 1f);
                ObjectAnimator card_anim_scaleY = ObjectAnimator.ofFloat(animatedCardViews.get(i), "scaleY", 0.1f, 1f);
                AnimatorSet cardSet = new AnimatorSet();
                cardSet.play(card_anim_alpha).with(card_anim_scaleX).with(card_anim_scaleY);
                cardSet.setDuration(500);
                cardSet.setStartDelay(CARD_START_DELAY * (i + 1));
                cardSet.setInterpolator(new AnticipateOvershootInterpolator(1f, 1.2f));
                cardSet.start();
            }
        }
    }
}
