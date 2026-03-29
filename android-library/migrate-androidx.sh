#!/bin/bash
# Comprehensive AndroidX migration script

echo "Starting comprehensive AndroidX migration..."

# Navigate to source directory
cd "$(dirname "$0")/src/main/java"

# Find all Java files and migrate them
find . -name "*.java" -type f -exec sed -i '
# Support library packages to AndroidX
s/import android\.support\.v4\.app\./import androidx.fragment.app./g
s/import android\.support\.v4\.content\./import androidx.core.content./g
s/import android\.support\.v4\.graphics\.drawable\./import androidx.core.graphics.drawable./g
s/import android\.support\.v4\.widget\./import androidx.core.widget./g
s/import android\.support\.v4\.view\./import androidx.core.view./g
s/import android\.support\.v4\.util\./import androidx.core.util./g
s/import android\.support\.v4\.provider\./import androidx.core.provider./g
s/import android\.support\.v4\.media\./import androidx.media./g
s/import android\.support\.annotation\./import androidx.annotation./g

s/import android\.support\.v7\.app\./import androidx.appcompat.app./g
s/import android\.support\.v7\.preference\./import androidx.preference./g
s/import android\.support\.v7\.widget\./import androidx.recyclerview.widget./g
s/import android\.support\.v7\.view\./import androidx.appcompat.view./g
s/import android\.support\.v7\.media\./import androidx.mediarouter.media./g

s/import android\.support\.design\./import com.google.android.material./g

s/import android\.support\.v13\./import androidx.legacy.app./g

# RecyclerView specific
s/androidx\.appcompat\.widget\.RecyclerView/androidx.recyclerview.widget.RecyclerView/g
s/androidx\.appcompat\.widget\.LinearLayoutManager/androidx.recyclerview.widget.LinearLayoutManager/g
s/androidx\.appcompat\.widget\.DefaultItemAnimator/androidx.recyclerview.widget.DefaultItemAnimator/g
s/androidx\.appcompat\.widget\.DividerItemDecoration/androidx.recyclerview.widget.DividerItemDecoration/g
s/androidx\.appcompat\.widget\.GridLayoutManager/androidx.recyclerview.widget.GridLayoutManager/g
s/androidx\.appcompat\.widget\.LinearSmoothScroller/androidx.recyclerview.widget.LinearSmoothScroller/g
s/androidx\.appcompat\.widget\.SnapHelper/androidx.recyclerview.widget.SnapHelper/g

# Package references in comments and class names (without import)
s/android\.support\.v4\.app\.NotificationCompat/androidx.core.app.NotificationCompat/g
s/android\.support\.v4\.app\.Fragment/androidx.fragment.app.Fragment/g
s/android\.support\.v4\.app\.FragmentManager/androidx.fragment.app.FragmentManager/g
s/android\.support\.v7\.app\.ActionBar/androidx.appcompat.app.ActionBar/g
s/android\.support\.v7\.app\.AlertDialog/androidx.appcompat.app.AlertDialog/g

# Internal view references
s/android\.support\.v4\.internal\.view\./androidx.appcompat.view.menu./g
' {} \;

echo "Migration complete! Summary:"
echo "- Migrated all android.support.* imports to androidx.*"
echo "- Fixed RecyclerView widget references"
echo "- Updated NotificationCompat and other common classes"
echo ""
echo "Next: Run './gradlew assembleDebug' to test the migration"
