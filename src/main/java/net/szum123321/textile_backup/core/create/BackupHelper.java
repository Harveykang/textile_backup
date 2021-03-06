/*
    A simple backup mod for Fabric
    Copyright (C) 2020  Szum123321

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package net.szum123321.textile_backup.core.create;

import net.minecraft.server.command.ServerCommandSource;
import net.szum123321.textile_backup.Statics;
import net.szum123321.textile_backup.core.Utilities;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class BackupHelper {
	public static Runnable create(BackupContext ctx) {
		StringBuilder builder = new StringBuilder();

		builder.append("Backup started ");

		builder.append(ctx.getInitiator().getPrefix());

		if(ctx.startedByPlayer()) {
			builder.append(ctx.getCommandSource().getDisplayName().getString());
		} else {
			builder.append(ctx.getInitiator().getName());
		}

		builder.append(" on: ");
		builder.append(Utilities.getDateTimeFormatter().format(LocalDateTime.now()));

		Statics.LOGGER.info(builder.toString());

		if (ctx.shouldSave()) {
			Statics.LOGGER.sendInfo(ctx.getCommandSource(), "Saving server...");
			Statics.LOGGER.info( "Saving server...");
			ctx.getServer().save(true, true, false);
		}

		return new MakeBackupRunnable(ctx);
	}

	public static int executeFileLimit(ServerCommandSource ctx, String worldName) {
		File root = Utilities.getBackupRootPath(worldName);
		AtomicInteger deletedFiles = new AtomicInteger();

		if (root.isDirectory() && root.exists() && root.listFiles() != null) {
			if (Statics.CONFIG.maxAge > 0) { // delete files older that configured
				final LocalDateTime now = LocalDateTime.now();

				Arrays.stream(root.listFiles())
						.filter(Utilities::isValidBackup)// We check if we can get file's creation date so that the next line won't throw an exception
						.filter(f -> now.toEpochSecond(ZoneOffset.UTC) - Utilities.getFileCreationTime(f).get().toEpochSecond(ZoneOffset.UTC) > Statics.CONFIG.maxAge)
						.forEach(f -> {
							if(deleteFile(f, ctx))
								deletedFiles.getAndIncrement();
						});
			}

			if (Statics.CONFIG.backupsToKeep > 0 && root.listFiles().length > Statics.CONFIG.backupsToKeep) {
				int i = root.listFiles().length;

				Iterator<File> it = Arrays.stream(root.listFiles())
						.filter(Utilities::isValidBackup)
						.sorted(Comparator.comparing(f -> Utilities.getFileCreationTime(f).get()))
						.iterator();

				while(i > Statics.CONFIG.backupsToKeep && it.hasNext()) {
					if(deleteFile(it.next(), ctx))
						deletedFiles.getAndIncrement();

					i--;
				}
			}

			if (Statics.CONFIG.maxSize > 0 && FileUtils.sizeOfDirectory(root) / 1024 > Statics.CONFIG.maxSize) {
				Iterator<File> it = Arrays.stream(root.listFiles())
						.filter(Utilities::isValidBackup)
						.sorted(Comparator.comparing(f -> Utilities.getFileCreationTime(f).get()))
						.iterator();

				while(FileUtils.sizeOfDirectory(root) / 1024 > Statics.CONFIG.maxSize && it.hasNext()) {
					if(deleteFile(it.next(), ctx))
						deletedFiles.getAndIncrement();
				}
			}
		}

		return deletedFiles.get();
	}

	private static boolean deleteFile(File f, ServerCommandSource ctx) {
		if(f != Statics.untouchableFile) {
			if(f.delete()) {
				Statics.LOGGER.sendInfo(ctx, "Deleting: {}", f.getName());
				Statics.LOGGER.info("Deleting: {}", f.getName());
				return true;
			} else {
				Statics.LOGGER.sendError(ctx, "Something went wrong while deleting: {}.", f.getName());
			}
		}

		return false;
	}
}