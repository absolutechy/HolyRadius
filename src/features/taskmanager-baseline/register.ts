// Side-effect module: defines background tasks at import time. Imported first by the app entry.
import { defineBaselineTask } from './baseline';

defineBaselineTask();
