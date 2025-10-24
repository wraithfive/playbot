import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { qotdApi } from '../api/client';
import type { UpdateQotdRequest } from '../types/qotd';
import { useWebSocket } from '../hooks/useWebSocket';

export default function QotdManager() {
  const { guildId } = useParams<{ guildId: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  // Channel selection state
  const [selectedChannelId, setSelectedChannelId] = useState<string | null>(null);
  const [csvFile, setCsvFile] = useState<File | null>(null);
  const [newQuestion, setNewQuestion] = useState('');
  const [configMessage, setConfigMessage] = useState('');
  const [questionMessage, setQuestionMessage] = useState('');

  interface ScheduleEntry {
    id: number;
    days: Set<string>;
    time: string;
  }

  const [schedules, setSchedules] = useState<ScheduleEntry[]>([
    { id: 1, days: new Set(['MON', 'TUE', 'WED', 'THU', 'FRI']), time: '09:00' }
  ]);
  const [showAdvanced, setShowAdvanced] = useState(false);

  // Fetch available channels
  const { data: channels } = useQuery({
    queryKey: ['qotd-channels', guildId],
    queryFn: async () => (await qotdApi.listChannels(guildId!)).data,
    enabled: !!guildId,
  });

  // Fetch all configured channels
  const { data: configuredChannels } = useQuery({
    queryKey: ['qotd-configs', guildId],
    queryFn: async () => (await qotdApi.listConfigs(guildId!)).data,
    enabled: !!guildId,
  });

  // Fetch config for selected channel
  const { data: config } = useQuery({
    queryKey: ['qotd-config', guildId, selectedChannelId],
    queryFn: async () => (await qotdApi.getConfig(guildId!, selectedChannelId!)).data,
    enabled: !!guildId && !!selectedChannelId,
  });

  // Fetch questions for selected channel (WebSocket handles real-time updates)
  const { data: questions } = useQuery({
    queryKey: ['qotd-questions', guildId, selectedChannelId],
    queryFn: async () => (await qotdApi.listQuestions(guildId!, selectedChannelId!)).data,
    enabled: !!guildId && !!selectedChannelId,
  });

  // Fetch guild-wide pending submissions (WebSocket handles real-time updates)
  const { data: submissions } = useQuery({
    queryKey: ['qotd-submissions', guildId],
    queryFn: async () => (await qotdApi.listPending(guildId!)).data,
    enabled: !!guildId,
  });

  const [form, setForm] = useState<UpdateQotdRequest>({
    enabled: false,
    timezone: 'UTC',
    randomize: false,
    autoApprove: false,
  });

  // Sync form state with backend config
  useEffect(() => {
    if (config) {
      setForm({
        enabled: config.enabled,
        timezone: config.timezone || 'UTC',
        advancedCron: config.scheduleCron || undefined,
        randomize: config.randomize,
        autoApprove: config.autoApprove,
        timeOfDay: '09:00',
        daysOfWeek: ['MON','TUE','WED','THU','FRI'],
      });
    }
  }, [config]);

  // Listen for real-time QOTD updates via WebSocket
  useWebSocket((message) => {
    if (message.guildId !== guildId) return; // Ignore updates for other guilds
    
    if (message.type === 'QOTD_QUESTIONS_CHANGED') {
      // Refresh questions for the affected channel
      if (message.channelId === selectedChannelId) {
        qc.invalidateQueries({ queryKey: ['qotd-questions', guildId, message.channelId] });
      }
    } else if (message.type === 'QOTD_SUBMISSIONS_CHANGED') {
      // Refresh submissions list
      qc.invalidateQueries({ queryKey: ['qotd-submissions', guildId] });
    }
  });

  const updateConfigMutation = useMutation({
    mutationFn: (req: UpdateQotdRequest) => qotdApi.updateConfig(guildId!, selectedChannelId!, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['qotd-config', guildId, selectedChannelId] });
      qc.invalidateQueries({ queryKey: ['qotd-configs', guildId] });
      setConfigMessage('✓ Saved configuration');
      setTimeout(() => setConfigMessage(''), 5000);
    },
    onError: () => {
      setConfigMessage('✗ Failed to save configuration');
      setTimeout(() => setConfigMessage(''), 5000);
    },
  });

  const uploadCsvMutation = useMutation({
    mutationFn: (file: File) => qotdApi.uploadCsv(guildId!, selectedChannelId!, file),
    onSuccess: (res) => {
      const r = res.data;
      setQuestionMessage(`✓ Added ${r.successCount} questions${r.failureCount ? `, ${r.failureCount} failed` : ''}`);
      qc.invalidateQueries({ queryKey: ['qotd-questions', guildId, selectedChannelId] });
      setCsvFile(null);
      setTimeout(() => setQuestionMessage(''), 5000);
    },
    onError: () => {
      setQuestionMessage('✗ Failed to upload CSV');
      setTimeout(() => setQuestionMessage(''), 5000);
    },
  });

  const addQuestionMutation = useMutation({
    mutationFn: (text: string) => qotdApi.addQuestion(guildId!, selectedChannelId!, text),
    onSuccess: () => {
      setNewQuestion('');
      setQuestionMessage('✓ Question added');
      qc.invalidateQueries({ queryKey: ['qotd-questions', guildId, selectedChannelId] });
      setTimeout(() => setQuestionMessage(''), 5000);
    },
    onError: () => {
      setQuestionMessage('✗ Failed to add question');
      setTimeout(() => setQuestionMessage(''), 5000);
    },
  });

  const deleteQuestionMutation = useMutation({
    mutationFn: (id: number) => qotdApi.deleteQuestion(guildId!, selectedChannelId!, id),
    onSuccess: () => {
      setQuestionMessage('✓ Question deleted');
      qc.invalidateQueries({ queryKey: ['qotd-questions', guildId, selectedChannelId] });
      setTimeout(() => setQuestionMessage(''), 5000);
    },
    onError: () => {
      setQuestionMessage('✗ Failed to delete question');
      setTimeout(() => setQuestionMessage(''), 5000);
    },
  });

  // Get channel name for display
  const getChannelName = (channelId: string) => {
    return channels?.find(ch => ch.id === channelId)?.name || channelId;
  };

  return (
    <div className="main-content">
      <nav className="server-nav">
        <div className="server-nav-container">
          <div className="server-nav-left">
            <button className="btn btn-link" onClick={() => navigate('/servers')}>← Back to Servers</button>
          </div>
          <div className="server-nav-tabs">
            <button className="server-nav-tab" onClick={() => navigate(`/servers/${guildId}`)}>
              🎨 Gacha Config
            </button>
            <button className="server-nav-tab active">
              💬 QOTD Config
            </button>
          </div>
        </div>
      </nav>

      <div className="server-content">
        <div className="info-notice">
          <h3>Question of the Day - Per Channel</h3>
          <p>Configure separate question lists and schedules for each channel.</p>
        </div>

        {/* Channel Selection */}
        <section className="action-card" style={{ marginBottom: '1rem' }}>
          <h3>Select Channel</h3>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
            {channels?.map((ch) => (
              <button
                key={ch.id}
                className={selectedChannelId === ch.id ? 'btn btn-primary btn-sm' : 'btn btn-secondary btn-sm'}
                onClick={() => setSelectedChannelId(ch.id)}
              >
                {ch.name}
                {configuredChannels?.some(cfg => cfg.channelId === ch.id && cfg.enabled) && ' ✓'}
              </button>
            ))}
          </div>
        </section>

        {/* Show channel config only when channel is selected */}
        {selectedChannelId && (
          <>
            <section className="action-card" style={{ marginBottom: '1rem' }}>
              <h3>Configuration for {getChannelName(selectedChannelId)}</h3>
              {/* Queue status */}
              <div style={{ marginBottom: '0.5rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                {(() => {
                  const remaining = questions?.length ?? 0;
                  if (remaining === 0) {
                    return (
                      <span style={{
                        background: '#ffe6e6', color: '#a80000', padding: '4px 8px', borderRadius: 4,
                        border: '1px solid #ffb3b3', fontSize: '0.85rem'
                      }}>
                        ❗ No questions remaining — posting will pause until you add more.
                      </span>
                    );
                  }
                  if (remaining <= 3) {
                    return (
                      <span style={{
                        background: '#fff7e6', color: '#8a6100', padding: '4px 8px', borderRadius: 4,
                        border: '1px solid #ffe0a3', fontSize: '0.85rem'
                      }}>
                        ⚠ Only {remaining} question{remaining === 1 ? '' : 's'} left in the queue.
                      </span>
                    );
                  }
                  return (
                    <span style={{
                      background: '#eef7ff', color: '#0b61a4', padding: '4px 8px', borderRadius: 4,
                      border: '1px solid #cfe7ff', fontSize: '0.85rem'
                    }}>
                      Queue: {remaining} remaining
                    </span>
                  );
                })()}
              </div>
              <div className="add-role-form">
                <div className="form-row">
                  <div className="field">
                    <label className="field-label">Enabled</label>
                    <input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} />
                  </div>
                  <div className="field">
                    <label className="field-label">Randomize order</label>
                    <input type="checkbox" checked={form.randomize} onChange={(e) => setForm({ ...form, randomize: e.target.checked })} />
                  </div>
                  <div className="field">
                    <label className="field-label">
                      Auto-approve submissions
                      <span style={{ fontSize: '0.85rem', color: '#999', fontWeight: 'normal', marginLeft: '0.5rem' }}>
                        (submissions instantly added to queue)
                      </span>
                    </label>
                    <input type="checkbox" checked={form.autoApprove} onChange={(e) => setForm({ ...form, autoApprove: e.target.checked })} />
                  </div>
                </div>
                <div className="form-row">
                  <div className="field">
                    <label className="field-label">Timezone</label>
                    <select 
                      className="input" 
                      value={form.timezone} 
                      onChange={(e) => setForm({ ...form, timezone: e.target.value })}
                    >
                      <optgroup label="US & Canada">
                        <option value="America/New_York">Eastern Time (ET)</option>
                        <option value="America/Chicago">Central Time (CT)</option>
                        <option value="America/Denver">Mountain Time (MT)</option>
                        <option value="America/Phoenix">Arizona (MST)</option>
                        <option value="America/Los_Angeles">Pacific Time (PT)</option>
                        <option value="America/Anchorage">Alaska (AKT)</option>
                        <option value="Pacific/Honolulu">Hawaii (HST)</option>
                      </optgroup>
                      <optgroup label="Europe">
                        <option value="Europe/London">London (GMT/BST)</option>
                        <option value="Europe/Paris">Paris (CET/CEST)</option>
                        <option value="Europe/Berlin">Berlin (CET/CEST)</option>
                        <option value="Europe/Rome">Rome (CET/CEST)</option>
                        <option value="Europe/Madrid">Madrid (CET/CEST)</option>
                        <option value="Europe/Amsterdam">Amsterdam (CET/CEST)</option>
                        <option value="Europe/Brussels">Brussels (CET/CEST)</option>
                        <option value="Europe/Vienna">Vienna (CET/CEST)</option>
                        <option value="Europe/Stockholm">Stockholm (CET/CEST)</option>
                        <option value="Europe/Athens">Athens (EET/EEST)</option>
                        <option value="Europe/Moscow">Moscow (MSK)</option>
                      </optgroup>
                      <optgroup label="Asia & Pacific">
                        <option value="Asia/Dubai">Dubai (GST)</option>
                        <option value="Asia/Kolkata">India (IST)</option>
                        <option value="Asia/Shanghai">China (CST)</option>
                        <option value="Asia/Hong_Kong">Hong Kong (HKT)</option>
                        <option value="Asia/Singapore">Singapore (SGT)</option>
                        <option value="Asia/Tokyo">Tokyo (JST)</option>
                        <option value="Asia/Seoul">Seoul (KST)</option>
                        <option value="Australia/Sydney">Sydney (AEDT/AEST)</option>
                        <option value="Australia/Melbourne">Melbourne (AEDT/AEST)</option>
                        <option value="Pacific/Auckland">Auckland (NZDT/NZST)</option>
                      </optgroup>
                      <optgroup label="Americas">
                        <option value="America/Toronto">Toronto (ET)</option>
                        <option value="America/Vancouver">Vancouver (PT)</option>
                        <option value="America/Mexico_City">Mexico City (CST)</option>
                        <option value="America/Sao_Paulo">São Paulo (BRT)</option>
                        <option value="America/Buenos_Aires">Buenos Aires (ART)</option>
                      </optgroup>
                      <optgroup label="Other">
                        <option value="UTC">UTC</option>
                        <option value="Africa/Cairo">Cairo (EET)</option>
                        <option value="Africa/Johannesburg">Johannesburg (SAST)</option>
                      </optgroup>
                    </select>
                  </div>
                </div>

                {/* Schedule Builder - same as before */}
                <div style={{ marginTop: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                    <label className="field-label">Schedules</label>
                    <button 
                      className="btn btn-link" 
                      onClick={() => setShowAdvanced(!showAdvanced)}
                      style={{ fontSize: '0.85rem' }}
                    >
                      {showAdvanced ? '← Simple Mode' : 'Advanced (cron) →'}
                    </button>
                  </div>

                  {!showAdvanced ? (
                    <>
                      {schedules.map((sched, idx) => (
                        <div key={sched.id} className="schedule-row" style={{ 
                          display: 'flex', 
                          alignItems: 'center', 
                          gap: '0.75rem', 
                          marginBottom: '0.75rem',
                          padding: '0.75rem',
                          background: 'var(--card-bg)',
                          borderRadius: '6px',
                          border: '1px solid var(--border-color)'
                        }}>
                          <div style={{ flex: 1 }}>
                            <div style={{ fontSize: '0.85rem', color: '#999', marginBottom: '0.25rem' }}>Days</div>
                            <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
                              {['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'].map((day) => (
                                <button
                                  key={day}
                                  className={sched.days.has(day) ? 'btn btn-primary btn-sm' : 'btn btn-secondary btn-sm'}
                                  onClick={() => {
                                    const newSchedules = [...schedules];
                                    const newDays = new Set(newSchedules[idx].days);
                                    if (newDays.has(day)) {
                                      newDays.delete(day);
                                    } else {
                                      newDays.add(day);
                                    }
                                    newSchedules[idx] = { ...newSchedules[idx], days: newDays };
                                    setSchedules(newSchedules);
                                  }}
                                  style={{ minWidth: '42px', padding: '0.25rem 0.5rem' }}
                                >
                                  {day.substring(0, 1)}
                                </button>
                              ))}
                            </div>
                          </div>
                          <div style={{ minWidth: '110px' }}>
                            <div style={{ fontSize: '0.85rem', color: '#999', marginBottom: '0.25rem' }}>Time</div>
                            <input
                              type="time"
                              className="input"
                              value={sched.time}
                              onChange={(e) => {
                                const newSchedules = [...schedules];
                                newSchedules[idx] = { ...newSchedules[idx], time: e.target.value };
                                setSchedules(newSchedules);
                              }}
                              style={{ fontSize: '0.9rem' }}
                            />
                          </div>
                          <button
                            className="btn btn-danger btn-sm"
                            onClick={() => setSchedules(schedules.filter((_, i) => i !== idx))}
                            disabled={schedules.length === 1}
                            style={{ marginTop: '1.2rem' }}
                          >
                            ✕
                          </button>
                        </div>
                      ))}
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => {
                          const newId = Math.max(...schedules.map(s => s.id), 0) + 1;
                          setSchedules([...schedules, { id: newId, days: new Set(['MON']), time: '09:00' }]);
                        }}
                        style={{ marginTop: '0.5rem' }}
                      >
                        + Add Schedule
                      </button>
                    </>
                  ) : (
                    <div className="field" style={{ minWidth: 260 }}>
                      <label className="field-label">Advanced (cron)</label>
                      <input 
                        className="input" 
                        placeholder="e.g., 0 0 9 ? * MON-FRI" 
                        value={form.advancedCron || ''} 
                        onChange={(e) => setForm({ ...form, advancedCron: e.target.value })} 
                      />
                    </div>
                  )}
                </div>

                <div className="form-actions">
                  <button className="btn btn-primary btn-sm" onClick={() => {
                    const requestData: UpdateQotdRequest = { ...form };
                    
                    if (!showAdvanced && schedules.length > 0) {
                      const allDays = new Set<string>();
                      const times = new Set<string>();
                      
                      schedules.forEach(sched => {
                        if (sched.days.size > 0) {
                          sched.days.forEach(day => allDays.add(day));
                          times.add(sched.time);
                        }
                      });

                      const firstTime = schedules[0]?.time || '09:00';
                      requestData.daysOfWeek = Array.from(allDays);
                      requestData.timeOfDay = firstTime;
                      requestData.advancedCron = null;

                      if (times.size > 1) {
                        setConfigMessage(`⚠ Multiple times detected. Using ${firstTime}. Days are combined. Use Advanced mode for different times.`);
                        setTimeout(() => setConfigMessage(''), 8000);
                      }
                    }
                    
                    updateConfigMutation.mutate(requestData);
                  }}>Save</button>
                  <button
                    className="btn btn-secondary btn-sm"
                    disabled={!form.enabled}
                    title={!form.enabled ? 'Enable QOTD for this channel to use Post Now' : ''}
                    style={!form.enabled ? { opacity: 0.6, cursor: 'not-allowed' } : {}}
                    onClick={async () => {
                      if (!guildId || !selectedChannelId || !form.enabled) return;
                      try {
                        await qotdApi.postNow(guildId, selectedChannelId);
                        setConfigMessage('✓ Posted next question');
                        qc.invalidateQueries({ queryKey: ['qotd-questions', guildId, selectedChannelId] });
                        setTimeout(() => setConfigMessage(''), 5000);
                      } catch {
                        setConfigMessage('✗ Failed to post (queue empty or permissions)');
                        setTimeout(() => setConfigMessage(''), 5000);
                      }
                    }}
                  >
                    Post Now
                  </button>
                  {!form.enabled && (
                    <div style={{ color: '#a80000', fontSize: '0.95em', marginTop: 4 }}>
                      Enable QOTD for this channel to test posting.
                    </div>
                  )}
                  <button className="btn btn-secondary btn-sm" onClick={() => {
                    const clearedForm = { ...form, enabled: false, advancedCron: '', daysOfWeek: [], timeOfDay: '' };
                    setForm(clearedForm);
                    setSchedules([{ id: 1, days: new Set(['MON', 'TUE', 'WED', 'THU', 'FRI']), time: '09:00' }]);
                    updateConfigMutation.mutate(clearedForm);
                  }}>Clear Schedule</button>
                  {config?.nextRuns?.length && (form.enabled && (form.advancedCron || schedules.some(s => s.days.size > 0))) ? (
                    <span className="selection-count">Next runs: {config.nextRuns.slice(0,3).join(', ')}</span>
                  ) : null}
                </div>
                {configMessage && (
                  <div className={`upload-message-inline ${configMessage.startsWith('✓') ? 'success' : 'error'}`}>{configMessage}</div>
                )}
              </div>
            </section>

            {/* Questions section for selected channel */}
            <section className="action-card">
              <div style={{ marginBottom: '1.5rem' }}>
                <h3 style={{ marginBottom: '0.5rem' }}>Questions for #{getChannelName(selectedChannelId)}</h3>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                  <span style={{ 
                    background: questions?.length === 0 ? '#ffe6e6' : questions?.length && questions.length <= 3 ? '#fff7e6' : '#eef7ff',
                    color: questions?.length === 0 ? '#a80000' : questions?.length && questions.length <= 3 ? '#8a6100' : '#0b61a4',
                    padding: '6px 12px', 
                    borderRadius: '6px',
                    border: `1px solid ${questions?.length === 0 ? '#ffb3b3' : questions?.length && questions.length <= 3 ? '#ffe0a3' : '#cfe7ff'}`,
                    fontSize: '0.9rem',
                    fontWeight: 500
                  }}>
                    {questions?.length === 0 ? '❗ ' : questions?.length && questions.length <= 3 ? '⚠ ' : '📋 '}
                    {questions?.length || 0} question{questions?.length === 1 ? '' : 's'}
                  </span>
                  {questions?.length === 0 && (
                    <span style={{ fontSize: '0.9rem', color: '#a80000' }}>
                      Empty queue — add questions or upload a CSV
                    </span>
                  )}
                  {questions && questions.length > 0 && questions.length <= 3 && (
                    <span style={{ fontSize: '0.9rem', color: '#8a6100' }}>
                      Low queue — consider adding more
                    </span>
                  )}
                </div>
              </div>
              
              <div className="add-role-form">
                {/* Add question input */}
                <div style={{ marginBottom: '1.5rem' }}>
                  <label className="field-label" style={{ marginBottom: '0.5rem', display: 'block' }}>Add Single Question</label>
                  <div className="form-row" style={{ gap: '0.75rem' }}>
                    <input
                      className="input"
                      placeholder="Enter a question..."
                      value={newQuestion}
                      onChange={(e) => setNewQuestion(e.target.value)}
                      onKeyDown={(e) => { if (e.key === 'Enter' && newQuestion.trim()) addQuestionMutation.mutate(newQuestion); }}
                      style={{ flex: 1 }}
                    />
                    <button 
                      className="btn btn-primary btn-sm" 
                      onClick={() => addQuestionMutation.mutate(newQuestion)} 
                      disabled={!newQuestion.trim()}
                      style={{ minWidth: '120px' }}
                    >
                      Add Question
                    </button>
                  </div>
                </div>

                {/* CSV Upload */}
                <div style={{ marginBottom: '1.5rem' }}>
                  <label className="field-label" style={{ marginBottom: '0.5rem', display: 'block' }}>Bulk Upload from CSV</label>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                    <a
                      href={guildId ? `/api/servers/${guildId}/qotd/download-example` : '#'}
                      className="btn btn-secondary btn-sm"
                      download
                      style={{ textDecoration: 'none' }}
                    >
                      📥 Download Example CSV
                    </a>
                    <div style={{ 
                      flex: 1, 
                      minWidth: '250px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.75rem',
                      padding: '0.75rem',
                      background: 'var(--card-bg)',
                      border: '2px dashed var(--border-color)',
                      borderRadius: '8px'
                    }}>
                      <label htmlFor="csv-upload" style={{ 
                        flex: 1,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.5rem'
                      }}>
                        <span style={{ fontSize: '1.2rem' }}>📄</span>
                        <span style={{ fontSize: '0.9rem', color: csvFile ? 'var(--text-color)' : '#999' }}>
                          {csvFile ? csvFile.name : 'Choose a CSV file...'}
                        </span>
                      </label>
                      <input 
                        id="csv-upload"
                        type="file" 
                        accept=".csv" 
                        onChange={(e) => setCsvFile(e.target.files?.[0] || null)}
                        style={{ display: 'none' }}
                      />
                      {csvFile && (
                        <button 
                          className="btn btn-sm"
                          onClick={() => setCsvFile(null)}
                          style={{ padding: '0.25rem 0.5rem', fontSize: '0.85rem' }}
                        >
                          ✕
                        </button>
                      )}
                    </div>
                    <button 
                      className="btn btn-primary btn-sm" 
                      onClick={() => { if (csvFile) uploadCsvMutation.mutate(csvFile); }} 
                      disabled={!csvFile}
                      style={{ minWidth: '100px' }}
                    >
                      📤 Upload CSV
                    </button>
                  </div>
                </div>

                {questionMessage && (
                  <div className={`upload-message-inline ${questionMessage.startsWith('✓') ? 'success' : 'error'}`} style={{ marginBottom: '1rem' }}>
                    {questionMessage}
                  </div>
                )}

                {/* Questions List */}
                {questions && questions.length > 0 && (
                  <div>
                    <label className="field-label" style={{ marginBottom: '0.75rem', display: 'block' }}>
                      Question Queue
                    </label>
                    <div className="role-list" style={{ gap: '0.75rem' }}>
                      {questions.map((q, index) => (
                        <div key={q.id} className="role-item" style={{ 
                          padding: '1rem',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '1rem'
                        }}>
                          <span style={{ 
                            fontSize: '0.85rem', 
                            color: '#999', 
                            minWidth: '2rem',
                            textAlign: 'center',
                            fontWeight: 500
                          }}>
                            #{index + 1}
                          </span>
                          <div style={{ flex: 1 }}>
                            <div className="role-name" style={{ lineHeight: 1.5, marginBottom: q.authorUsername ? '0.25rem' : 0 }}>
                              {q.text}
                            </div>
                            {q.authorUsername && (
                              <div style={{ fontSize: '0.85rem', color: '#999', fontStyle: 'italic' }}>
                                by @{q.authorUsername}
                              </div>
                            )}
                          </div>
                          <button 
                            className="btn btn-danger btn-sm" 
                            onClick={() => {
                              if (window.confirm(`Delete this question?\n\n"${q.text}"`)) {
                                deleteQuestionMutation.mutate(q.id);
                              }
                            }}
                            style={{ minWidth: '80px' }}
                          >
                            Delete
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                
                {questions && questions.length === 0 && (
                  <div style={{
                    padding: '2rem',
                    textAlign: 'center',
                    background: 'var(--card-bg)',
                    borderRadius: '8px',
                    border: '2px dashed var(--border-color)',
                    color: '#999'
                  }}>
                    <div style={{ fontSize: '3rem', marginBottom: '0.5rem' }}>📝</div>
                    <div style={{ fontSize: '1.1rem', marginBottom: '0.25rem' }}>No questions yet</div>
                    <div style={{ fontSize: '0.9rem' }}>Add your first question above or upload a CSV file</div>
                  </div>
                )}
              </div>
            </section>
          </>
        )}

        {/* Submissions - guild-wide, shown regardless of channel selection */}
        {(submissions && submissions.length > 0) && (
          <section className="action-card" style={{ marginTop: '1rem' }}>
            <h3>Pending User Submissions ({submissions.length})</h3>
            <p className="section-description">Users submitted these questions via <code>/qotd-submit</code>. Select a channel above, then approve submissions to add them to that channel.</p>
            {!selectedChannelId && (
              <div className="info-notice" style={{ marginTop: '0.5rem', marginBottom: '1rem' }}>
                ⚠️ Select a channel above to approve submissions
              </div>
            )}
            <div className="role-list">
              {submissions.map((sub) => (
                <div key={sub.id} className="role-item">
                  <span className="role-name">{sub.text}</span>
                  <span style={{ fontSize: '0.85rem', color: '#999', marginLeft: '0.5rem' }}>by {sub.username}</span>
                  {selectedChannelId && (
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button className="btn btn-primary btn-sm" onClick={async () => {
                        try {
                          await qotdApi.approve(guildId!, selectedChannelId!, sub.id);
                          setQuestionMessage(`✓ Approved and added to ${getChannelName(selectedChannelId)}`);
                          qc.invalidateQueries({ queryKey: ['qotd-submissions', guildId] });
                          qc.invalidateQueries({ queryKey: ['qotd-questions', guildId, selectedChannelId] });
                          setTimeout(() => setQuestionMessage(''), 5000);
                        } catch {
                          setQuestionMessage('✗ Failed to approve');
                          setTimeout(() => setQuestionMessage(''), 5000);
                        }
                      }}>Approve</button>
                      <button className="btn btn-danger btn-sm" onClick={async () => {
                        try {
                          await qotdApi.reject(guildId!, sub.id);
                          setQuestionMessage('✓ Rejected');
                          qc.invalidateQueries({ queryKey: ['qotd-submissions', guildId] });
                          setTimeout(() => setQuestionMessage(''), 5000);
                        } catch {
                          setQuestionMessage('✗ Failed to reject');
                          setTimeout(() => setQuestionMessage(''), 5000);
                        }
                      }}>Reject</button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}
