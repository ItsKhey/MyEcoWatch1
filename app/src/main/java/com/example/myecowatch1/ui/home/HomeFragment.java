package com.example.myecowatch1.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myecowatch1.core.R;
import com.example.myecowatch1.data.models.Report;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.ui.reports.ReportsViewModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HomeFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private ReportsViewModel reportsViewModel;
    private AuthRepository authRepository;
    private TextView pendingCount, completedCount, deniedCount, overallCount;
    private LinearLayout recentActivityList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        pendingCount = view.findViewById(R.id.pendingCount);
        completedCount = view.findViewById(R.id.completedCount);
        deniedCount = view.findViewById(R.id.deniedCount);
        overallCount = view.findViewById(R.id.overallCount);
        recentActivityList = view.findViewById(R.id.recentActivityList);

        view.findViewById(R.id.btnViewAll).setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                ((DashboardActivity) getActivity()).navigateToReports();
            }
        });

        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        authRepository = new AuthRepository(requireActivity().getApplication());

        reportsViewModel.getAllReports().observe(getViewLifecycleOwner(), this::updateUI);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            reportsViewModel.refreshReports();
            authRepository.fetchMe(null);
            swipeRefreshLayout.postDelayed(() -> {
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }, 3000);
        });

        reportsViewModel.refreshReports();

        return view;
    }

    private void updateUI(List<Report> reports) {
        updateStats(reports);
        updateRecentActivity(reports);
    }

    private void updateStats(List<Report> reports) {
        int pending = 0, completed = 0, denied = 0, validTotal = 0;
        if (reports != null) {
            for (Report r : reports) {
                // Exclude trashed reports completely from totals and home dashboard metrics
                if (r.isDeleted()) continue;

                validTotal++;
                if ("Pending".equalsIgnoreCase(r.getStatus())) pending++;
                else if ("Completed".equalsIgnoreCase(r.getStatus())) completed++;
                else if ("Denied".equalsIgnoreCase(r.getStatus())) denied++;
            }
            overallCount.setText(String.valueOf(validTotal));
        } else {
            overallCount.setText("0");
        }
        pendingCount.setText(String.valueOf(pending));
        completedCount.setText(String.valueOf(completed));
        deniedCount.setText(String.valueOf(denied));
        
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void updateRecentActivity(List<Report> reports) {
        if (recentActivityList == null) return;
        recentActivityList.removeAllViews();
        
        if (reports == null || reports.isEmpty()) {
            TextView emptyTv = new TextView(getContext());
            emptyTv.setText("No recent activities.");
            emptyTv.setPadding(0, 20, 0, 20);
            recentActivityList.addView(emptyTv);
            return;
        }

        // Sort by date (descending) and exclude deleted/trashed reports
        List<Report> sortedReports = new ArrayList<>();
        for (Report r : reports) {
            if (!r.isDeleted()) {
                sortedReports.add(r);
            }
        }

        if (sortedReports.isEmpty()) {
            TextView emptyTv = new TextView(getContext());
            emptyTv.setText("No recent activities.");
            emptyTv.setPadding(0, 20, 0, 20);
            recentActivityList.addView(emptyTv);
            return;
        }

        Collections.sort(sortedReports, (r1, r2) -> {
            String d1 = r1.getCreatedAt();
            String d2 = r2.getCreatedAt();
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return d2.compareTo(d1);
        });

        int count = Math.min(sortedReports.size(), 5);
        for (int i = 0; i < count; i++) {
            Report report = sortedReports.get(i);
            addActivityItem(report);
        }
    }

    private void addActivityItem(Report report) {
        View itemView = getLayoutInflater().inflate(R.layout.item_recent_activity, recentActivityList, false);
        TextView titleTv = itemView.findViewById(R.id.activityTitle);
        TextView descTv = itemView.findViewById(R.id.activityDescription);
        TextView timeTv = itemView.findViewById(R.id.activityTime);

        String status = report.getStatus();
        String title = "Report " + status;
        String icon = "🔄";
        if ("Pending".equalsIgnoreCase(status)) {
            title = "Report Submitted";
            icon = "📝";
        } else if ("Completed".equalsIgnoreCase(status)) {
            title = "Report Completed";
            icon = "✓";
        } else if ("Denied".equalsIgnoreCase(status)) {
            title = "Report Denied";
            icon = "✕";
        }

        titleTv.setText(title);
        descTv.setText(report.getTitle() + " report was " + status.toLowerCase() + ".");
        timeTv.setText(formatDateTime(report.getCreatedAt()));

        recentActivityList.addView(itemView);
    }

    private String formatDateTime(String timestamp) {
        if (timestamp == null) return "Just now";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(timestamp);
            
            if (date == null) return timestamp;

            TimeZone manilaZone = TimeZone.getTimeZone("Asia/Manila");
            Calendar now = Calendar.getInstance(manilaZone);
            Calendar target = Calendar.getInstance(manilaZone);
            target.setTime(date);

            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.US);
            timeFormat.setTimeZone(manilaZone);
            String timeStr = timeFormat.format(date);

            if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
                return "Today · " + timeStr;
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.US);
                dateFormat.setTimeZone(manilaZone);
                return dateFormat.format(date) + " · " + timeStr;
            }
        } catch (ParseException e) {
            return timestamp;
        }
    }
}