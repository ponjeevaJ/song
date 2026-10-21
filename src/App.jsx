import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Album, ArrowDownToLine, AudioLines, BadgeCheck, CalendarDays, ChevronDown,
  CircleHelp, Clock3, Disc3, Heart, Home, Languages, ListMusic, LoaderCircle,
  LogIn, Menu, Mic2, Music2, Pause, Play, Plus, Search, Settings2, Shuffle,
  SkipBack, SkipForward, Sparkles, Star, TrendingUp, Volume2, X,
} from 'lucide-react';

const API = (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '').replace(/\/api$/i, '');
const isYouTubePlayback = (song) => song?.playbackUrl?.startsWith('https://www.youtube.com/embed/');
const isAppleMusicPlayback = (song) => song?.providerUrl?.startsWith('https://music.apple.com/');
const appleMusicEmbedUrl = (song) => song.providerUrl.replace('https://music.apple.com/', 'https://embed.music.apple.com/');
const request = async (path, options = {}, token = '') => {
  let response;
  try {
    response = await fetch(`${API}/api${path}`, {
      ...options,
      headers: {
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new Error('Cannot reach the Isai World API. Make sure the backend and database are running, then try again.');
  }
  if (!response.ok) {
    const detail = await response.json().catch(() => ({}));
    const fallback = response.status === 403
      ? path.startsWith('/auth/')
        ? `The deployed login API returned 403 at ${new URL(response.url).origin}/api${path}. Check that VITE_API_BASE_URL points to the backend origin and that the backend or hosting proxy allows /api/auth/login.`
        : 'Access was denied by the API. Open Isai World at http://localhost:5173/ and try again. Administrator actions require an admin account.'
      : `Request failed (${response.status})`;
    const error = new Error(detail.message || fallback);
    error.status = response.status;
    throw error;
  }
  const body = await response.text();
  return body ? JSON.parse(body) : null;
};

const tamil = {
  'New Tamil Songs Today': 'இன்றைய புதிய பாடல்கள்',
  'Latest Tamil Movie Songs': 'சமீபத்திய திரைப்படப் பாடல்கள்',
  'Daily Music Updates': 'தினசரி இசை புதுப்பிப்புகள்',
  'Old Tamil Classics': 'பழைய தமிழ் கிளாசிக்ஸ்',
  '80s Golden Hits': '80களின் பொற்கால பாடல்கள்',
  '90s Tamil Hits': '90களின் தமிழ் ஹிட்ஸ்',
  '2000s Tamil Songs': '2000களின் தமிழ் பாடல்கள்',
  'Tamil Love Songs': 'தமிழ் காதல் பாடல்கள்',
  'Sad Melody Songs': 'சோக மெலடி பாடல்கள்',
  'Tamil Folk & Kuthu': 'தமிழ் நாட்டுப்புறம் & குத்து',
  'Tamil BGM & Instrumental': 'தமிழ் பின்னணி இசை',
  'Trending Songs': 'பிரபலமான பாடல்கள்',
  'Recently Played': 'சமீபத்தில் கேட்டவை',
};

const sections = [
  { title: 'New Tamil Songs Today', icon: Sparkles, key: 'new' },
  { title: 'Latest Tamil Movie Songs', icon: Disc3, key: 'latest' },
  { title: 'Daily Music Updates', icon: CalendarDays, key: 'daily' },
  { title: 'Old Tamil Classics', icon: Star, key: 'old' },
  { title: '80s Golden Hits', icon: AudioLines, key: '80s' },
  { title: '90s Tamil Hits', icon: AudioLines, key: '90s' },
  { title: '2000s Tamil Songs', icon: AudioLines, key: '2000s' },
  { title: 'Tamil Love Songs', icon: Heart, key: 'love' },
  { title: 'Sad Melody Songs', icon: Music2, key: 'sad' },
  { title: 'Tamil Folk & Kuthu', icon: AudioLines, key: 'folk' },
  { title: 'Tamil BGM & Instrumental', icon: Album, key: 'bgm' },
  { title: 'Trending Songs', icon: TrendingUp, key: 'trending' },
  { title: 'Songs by Singers & Music Directors', icon: Mic2, key: 'singers' },
  { title: 'Recently Played', icon: Clock3, key: 'recent' },
];

const featuredYouTubeSong = {
  id: 'youtube-featured:c3H8GjrklCk',
  title: 'Kalyana Virundhu - Video Song | DOROTHY',
  movie: 'DOROTHY',
  singer: 'Sony Music South',
  composer: 'Ilaiyaraaja',
  genre: 'Tamil',
  artworkUrl: 'https://i.ytimg.com/vi/c3H8GjrklCk/hqdefault.jpg',
  playbackUrl: 'https://www.youtube.com/embed/c3H8GjrklCk',
  providerUrl: 'https://www.youtube.com/watch?v=c3H8GjrklCk',
  isFeaturedFallback: true,
};

const youtubeSearchUrl = (section) => {
  const queries = {
    new: 'latest Tamil songs official',
    latest: 'latest Tamil movie songs official',
    daily: 'Tamil songs official',
    old: 'old Tamil classic songs official',
    '80s': '1980s Tamil songs official',
    '90s': '1990s Tamil songs official',
    '2000s': '2000s Tamil songs official',
    love: 'Tamil love songs official',
    sad: 'Tamil sad melody songs official',
    folk: 'Tamil folk kuthu songs official',
    bgm: 'Tamil BGM instrumental official',
    trending: 'trending Tamil songs official',
    singers: 'Tamil songs official music channel',
  };
  return `https://www.youtube.com/results?search_query=${encodeURIComponent(queries[section] || 'Tamil songs official')}`;
};
const youtubeTrackSearchUrl = (song) => `https://www.youtube.com/results?search_query=${encodeURIComponent(`${song.title} ${song.singer || ''} official`)}`;

const indiaNow = new Date();
const indiaDay = new Intl.DateTimeFormat('en', { weekday: 'long', timeZone: 'Asia/Kolkata' }).format(indiaNow).toUpperCase();
const indiaHour = Number(new Intl.DateTimeFormat('en', { hour: 'numeric', hourCycle: 'h23', timeZone: 'Asia/Kolkata' }).format(indiaNow));
const greeting = indiaHour < 12 ? 'Good morning' : indiaHour < 17 ? 'Good afternoon' : 'Good evening';

const matching = (song, key) => {
  const year = Number(String(song.releaseDate || '').slice(0, 4));
  const genre = `${song.genre || ''} ${song.title || ''}`.toLowerCase();
  if (key === 'new' || key === 'daily') {
    const today = new Date(Date.now() + 330 * 60 * 1000).toISOString().slice(0, 10);
    return song.releaseDate === today;
  }
  if (key === 'latest' || key === 'trending') return true;
  if (key === '80s') return year >= 1980 && year <= 1989;
  if (key === '90s') return year >= 1990 && year <= 1999;
  if (key === '2000s') return year >= 2000 && year <= 2009;
  if (key === 'old') return year > 0 && year < 2000;
  if (key === 'love') return /love|romance|romantic|காதல்/.test(genre);
  if (key === 'sad') return /sad|melody|மெலடி/.test(genre);
  if (key === 'folk') return /folk|kuthu|குத்து/.test(genre);
  if (key === 'bgm') return /bgm|instrumental|background/.test(genre);
  if (key === 'singers') return Boolean(song.singer || song.composer);
  return false;
};

function App() {
  const [songs, setSongs] = useState([]);
  const [trendingSongs, setTrendingSongs] = useState([]);
  const [dailyBeats, setDailyBeats] = useState([]);
  const [recent, setRecent] = useState([]);
  const [favorites, setFavorites] = useState(new Set());
  const [favoriteSongs, setFavoriteSongs] = useState([]);
  const [playlists, setPlaylists] = useState([]);
  const [libraryView, setLibraryView] = useState('');
  const [playlistDraft, setPlaylistDraft] = useState('');
  const [selectedPlaylist, setSelectedPlaylist] = useState('');
  const [playlistSongs, setPlaylistSongs] = useState({ playlistId: '', songs: [] });
  const [token, setToken] = useState(localStorage.getItem('isai-token') || '');
  const [role, setRole] = useState(localStorage.getItem('isai-role') || 'USER');
  const [profile, setProfile] = useState(localStorage.getItem('isai-email') || '');
  const [current, setCurrent] = useState(null);
  const [youtubePlayerOpen, setYoutubePlayerOpen] = useState(false);
  const [applePlayerOpen, setApplePlayerOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeSearch, setActiveSearch] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchYear, setSearchYear] = useState('');
  const [searchGenre, setSearchGenre] = useState('');
  const [language, setLanguage] = useState('en');
  const [authOpen, setAuthOpen] = useState(false);
  const [isRegister, setIsRegister] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [syncStatus, setSyncStatus] = useState(null);
  const [adminLogs, setAdminLogs] = useState([]);
  const [isPlaying, setIsPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [position, setPosition] = useState(0);
  const [volume, setVolume] = useState(0.75);
  const [shuffle, setShuffle] = useState(false);
  const [repeat, setRepeat] = useState(false);
  const [expandedSections, setExpandedSections] = useState(new Set());
  const [mobileMenu, setMobileMenu] = useState(false);
  const audioRef = useRef(null);
  const isTamil = language === 'ta';

  const refreshSongs = useCallback(async () => {
    try {
      const [catalog, trending, dailyPlaylist] = await Promise.all([
        request('/songs'),
        request('/songs/trending'),
        request('/playlists/today'),
      ]);
      setSongs(catalog);
      setTrendingSongs(trending);
      setDailyBeats(dailyPlaylist.songs);
      if (token) {
        const history = await request('/me/history', {}, token);
        setRecent(history);
      } else {
        setRecent([]);
      }
    } catch (e) {
      setError(e.message);
    }
  }, [token]);

  useEffect(() => {
    refreshSongs();
    request('/catalog/status').then(setSyncStatus).catch((e) => setError(e.message));
  }, [refreshSongs]);

  useEffect(() => {
    const needle = activeSearch.trim();
    if (!needle && !searchYear && !searchGenre) {
      setSearchResults([]);
      setSearchLoading(false);
      return undefined;
    }

    let cancelled = false;
    const timer = window.setTimeout(async () => {
      const params = new URLSearchParams();
      if (needle) params.set('query', needle);
      if (searchYear) params.set('year', searchYear);
      if (searchGenre) params.set('genre', searchGenre);
      setSearchLoading(true);
      try {
        const results = await request(`/songs?${params.toString()}`);
        if (!cancelled) {
          setSearchResults(results);
          requestAnimationFrame(() => document.getElementById('search-results')?.scrollIntoView({ behavior: 'smooth' }));
        }
      } catch (e) {
        if (!cancelled) setError(e.message);
      } finally {
        if (!cancelled) setSearchLoading(false);
      }
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [activeSearch, searchYear, searchGenre]);

  useEffect(() => {
    const handleSearchShortcut = (event) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        const input = document.getElementById('search-box');
        input?.focus();
        input?.select();
      }
    };
    window.addEventListener('keydown', handleSearchShortcut);
    return () => window.removeEventListener('keydown', handleSearchShortcut);
  }, []);

  useEffect(() => {
    const timer = window.setInterval(async () => {
      try {
        const status = await request('/catalog/status');
        setSyncStatus(status);
        await refreshSongs();
        if (role === 'ADMIN' && token) {
          setAdminLogs(await request('/admin/sync-logs', {}, token));
        }
      } catch (e) {
        setError(e.message);
      }
    }, 300_000);
    return () => window.clearInterval(timer);
  }, [refreshSongs, role, token]);

  useEffect(() => {
    if (!token) {
      setFavorites(new Set());
      return;
    }
    Promise.all([
      request('/me/favorites', {}, token),
      request('/me/playlists', {}, token),
    ])
      .then(([items, lists]) => {
        setFavoriteSongs(items);
        setFavorites(new Set(items.map((item) => item.id)));
        setPlaylists(lists);
        setSelectedPlaylist((current) => current || String(lists[0]?.id || ''));
      })
      .catch((e) => setError(e.message));
  }, [token]);

  useEffect(() => {
    if (role !== 'ADMIN' || !token) {
      setAdminLogs([]);
      return;
    }
    request('/admin/sync-logs', {}, token).then(setAdminLogs).catch((e) => setError(e.message));
  }, [role, token]);

  const todaySongs = useMemo(() => songs.filter((song) => matching(song, 'new')), [songs]);

  const playSong = async (song, queue = songs) => {
    if (isAppleMusicPlayback(song)) {
      audioRef.current?.pause();
      setCurrent(song);
      setIsPlaying(false);
      setYoutubePlayerOpen(false);
      setApplePlayerOpen(true);
      setRecent((old) => [song, ...old.filter((item) => item.id !== song.id)].slice(0, 50));
      window.__isaiQueue = queue;
      return;
    }
    if (!song.playbackUrl) {
      setError('Playback is unavailable for this track. The licensed provider has not supplied an audio stream.');
      return;
    }
    if (current?.id === song.id && audioRef.current) {
      if (isYouTubePlayback(song)) {
        setYoutubePlayerOpen(true);
        return;
      }
      audioRef.current.play().then(() => setIsPlaying(true)).catch(() => setError('Unable to start playback.'));
      return;
    }
    setCurrent(song);
    setIsPlaying(true);
    setApplePlayerOpen(false);
    setYoutubePlayerOpen(isYouTubePlayback(song));
    if (!song.isFeaturedFallback) {
      setRecent((old) => [song, ...old.filter((item) => item.id !== song.id)].slice(0, 50));
      if (token && !song.providerUrl) {
        request(`/me/history/${encodeURIComponent(song.id)}`, { method: 'POST' }, token).catch((e) => setError(e.message));
      }
    }
    window.__isaiQueue = queue;
  };

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    if (isAppleMusicPlayback(current)) {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
      return;
    }
    if (!current?.playbackUrl) return;
    if (isYouTubePlayback(current)) {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
      return;
    }
    audio.src = current.playbackUrl;
    audio.volume = volume;
    audio.play().catch(() => {
      setIsPlaying(false);
      setError('The licensed audio stream could not be played. Try another track or check provider access.');
    });
  }, [current, volume]);

  const stepSong = (direction) => {
    const queue = window.__isaiQueue?.length ? window.__isaiQueue : songs;
    if (!queue.length) return;
    const at = Math.max(0, queue.findIndex((song) => song.id === current?.id));
    const nextIndex = shuffle ? Math.floor(Math.random() * queue.length) : (at + direction + queue.length) % queue.length;
    playSong(queue[nextIndex], queue);
  };

  const togglePlay = () => {
    if (isAppleMusicPlayback(current)) {
      setApplePlayerOpen(true);
      return;
    }
    if (isYouTubePlayback(current)) {
      setYoutubePlayerOpen(true);
      return;
    }
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) audio.play().then(() => setIsPlaying(true)).catch(() => setError('Unable to start playback.'));
    else {
      audio.pause();
      setIsPlaying(false);
    }
  };

  const toggleFavorite = async (song) => {
    if (!token) {
      setAuthOpen(true);
      return;
    }
    const liked = favorites.has(song.id);
    try {
      await request(`/me/favorites/${encodeURIComponent(song.id)}`, { method: liked ? 'DELETE' : 'POST' }, token);
      setFavorites((old) => {
        const updated = new Set(old);
        if (liked) updated.delete(song.id);
        else updated.add(song.id);
        return updated;
      });
      setFavoriteSongs((old) => liked ? old.filter((item) => item.id !== song.id) : [...old, song]);
    } catch (e) {
      setError(e.message);
    }
  };

  const createPlaylist = async (event) => {
    event.preventDefault();
    try {
      const playlist = await request('/me/playlists', {
        method: 'POST',
        body: JSON.stringify({ name: playlistDraft, description: '' }),
      }, token);
      setPlaylists((old) => [playlist, ...old]);
      setSelectedPlaylist(String(playlist.id));
      setPlaylistDraft('');
    } catch (e) {
      setError(e.message);
    }
  };

  const addToPlaylist = async (song) => {
    if (!selectedPlaylist) {
      setLibraryView('playlists');
      setError('Create a playlist or select one before adding songs.');
      return;
    }
    try {
      await request(`/me/playlists/${selectedPlaylist}/songs/${encodeURIComponent(song.id)}`, { method: 'POST' }, token);
      if (String(selectedPlaylist) === String(playlistSongs.playlistId)) {
        const updated = await request(`/me/playlists/${selectedPlaylist}/songs`, {}, token);
        setPlaylistSongs((old) => ({ ...old, songs: updated }));
      }
      setPlaylists(await request('/me/playlists', {}, token));
    } catch (e) {
      setError(e.message);
    }
  };

  const openPlaylist = async (playlist) => {
    setSelectedPlaylist(String(playlist.id));
    try {
      const items = await request(`/me/playlists/${playlist.id}/songs`, {}, token);
      setPlaylistSongs({ playlistId: String(playlist.id), songs: items });
    } catch (e) {
      setError(e.message);
    }
  };

  const removeFromPlaylist = async (song) => {
    try {
      await request(`/me/playlists/${selectedPlaylist}/songs/${encodeURIComponent(song.id)}`, { method: 'DELETE' }, token);
      setPlaylistSongs((old) => ({ ...old, songs: old.songs.filter((item) => item.id !== song.id) }));
      setPlaylists(await request('/me/playlists', {}, token));
    } catch (e) {
      setError(e.message);
    }
  };

  const handleAuth = async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError('');
    try {
      const result = await request(`/auth/${isRegister ? 'register' : 'login'}`, {
        method: 'POST',
        body: JSON.stringify({ email: String(form.get('email') || '').trim(), password: form.get('password') }),
      });
      localStorage.setItem('isai-token', result.token);
      localStorage.setItem('isai-role', result.role);
      localStorage.setItem('isai-email', result.email);
      setToken(result.token);
      setRole(result.role);
      setProfile(result.email);
      setAuthOpen(false);
    } catch (e) {
      if (isRegister && e.status === 409) {
        setIsRegister(false);
        setError('This email already has an account. Sign in with that account below, or use a different email.');
      } else if (!isRegister && e.status === 401) {
        setError('Email or password is incorrect. Check the credentials you used when creating your account.');
      } else {
        setError(e.message);
      }
    } finally {
      setBusy(false);
    }
  };

  const syncNow = async () => {
    setBusy(true);
    setError('');
    try {
      await request('/admin/sync', { method: 'POST' }, token);
      const status = await request('/catalog/status');
      setSyncStatus(status);
      setAdminLogs(await request('/admin/sync-logs', {}, token));
      await refreshSongs();
    } catch (e) {
      let message = e.message;
      try {
        setSyncStatus(await request('/catalog/status'));
        setAdminLogs(await request('/admin/sync-logs', {}, token));
      } catch (refreshError) {
        message = `${message} Could not reload sync logs: ${refreshError.message}`;
      }
      setError(message);
    } finally {
      setBusy(false);
    }
  };

  const scrollTo = (id) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
    setMobileMenu(false);
  };
  const submitSearch = (event) => {
    event.preventDefault();
    setActiveSearch(query.trim());
    requestAnimationFrame(() => scrollTo('search-results'));
  };
  const openLibrary = (view) => {
    setLibraryView(view);
    requestAnimationFrame(() => scrollTo('library'));
  };

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileMenu ? 'sidebar-open' : ''}`}>
        <a className="brand" href="#" onClick={(e) => { e.preventDefault(); scrollTo('home'); }}>
          <span className="brand-mark"><AudioLines size={22} /></span>
          <span><strong>isai<span>world</span></strong><small>இசை உலகம்</small></span>
        </a>
        <div className="side-label">{isTamil ? 'பட்டியல்' : 'MENU'} <span>•••</span></div>
        <nav className="main-nav">
          <button className="nav-item selected" onClick={() => scrollTo('home')}><Home size={18} />{isTamil ? 'முகப்பு' : 'Home'}</button>
          <button className="nav-item" onClick={() => { document.getElementById('search-box')?.focus(); scrollTo('home'); }}><Search size={18} />{isTamil ? 'தேடல்' : 'Discover'}</button>
          <button className="nav-item" onClick={() => scrollTo('trending')}><TrendingUp size={18} />{isTamil ? 'பிரபலமானவை' : 'Trending'}</button>
          <button className="nav-item" onClick={() => scrollTo('new-releases')}><Sparkles size={18} />{isTamil ? 'புதிய வெளியீடுகள்' : 'New Releases'}</button>
        </nav>
        <div className="side-label collection-label">{isTamil ? 'உங்கள் சேமிப்பு' : 'YOUR COLLECTION'} <Plus size={15} /></div>
        <nav className="main-nav">
          <button className="nav-item" onClick={() => token ? openLibrary('favorites') : setAuthOpen(true)}><Heart size={18} />{isTamil ? 'விருப்பப் பாடல்கள்' : 'Liked Songs'}</button>
          <button className="nav-item" onClick={() => scrollTo('recent')}><Clock3 size={18} />{isTamil ? 'சமீபத்தில் கேட்டவை' : 'Recently Played'}</button>
          <button className="nav-item" onClick={() => token ? openLibrary('playlists') : setAuthOpen(true)}><ListMusic size={18} />{isTamil ? 'உங்கள் பட்டியல்கள்' : 'Your Playlists'}</button>
        </nav>
        <div className="sidebar-quote">
          <div className="quote-icon">❝</div>
          <p>இசை என்பது<br />மனதின் மொழி.</p>
          <span>MUSIC SPEAKS</span>
        </div>
        <div className="sidebar-bottom"><span className="online-dot" /> Curated for Tamil souls</div>
      </aside>

      <main className="main-area" id="home">
        <header className="topbar">
          <button className="icon-button mobile-menu-button" aria-label="Open menu" onClick={() => setMobileMenu(!mobileMenu)}><Menu /></button>
          <div className="breadcrumb">Isai World <span>/</span> <b>{isTamil ? 'கண்டறி' : 'Discover'}</b></div>
          <div className="topbar-actions">
            <form className="search-field" role="search" onSubmit={submitSearch}>
              <input id="search-box" list="catalog-suggestions" placeholder={isTamil ? 'பாடல், கலைஞர், படம்...' : 'Songs, artists, movies...'} value={query} onChange={(e) => setQuery(e.target.value)} />
              <datalist id="catalog-suggestions">{songs.slice(0, 8).map((song) => <option key={song.id} value={song.title} />)}</datalist>
              <button className="search-submit" type="submit" aria-label={isTamil ? 'தேடு' : 'Search'} title={isTamil ? 'தேடு' : 'Search'}><Search size={16} /></button>
              <kbd>Ctrl/⌘ K</kbd>
            </form>
            <button className="language-button" onClick={() => setLanguage(language === 'en' ? 'ta' : 'en')} title="Switch language"><Languages size={17} /><span>{language === 'en' ? 'EN' : 'தமிழ்'}</span><ChevronDown size={14} /></button>
            {profile ? (
              <button className="profile-chip" onClick={() => { ['isai-token', 'isai-role', 'isai-email'].forEach((key) => localStorage.removeItem(key)); setToken(''); setProfile(''); setRole('USER'); setLibraryView(''); setFavoriteSongs([]); setPlaylists([]); setRecent([]); setPlaylistSongs({ playlistId: '', songs: [] }); }}> <span className="avatar">{profile[0]?.toUpperCase()}</span><span className="profile-name">{profile.split('@')[0]}</span><span className="signout">Sign out</span></button>
            ) : <button className="login-button" onClick={() => setAuthOpen(true)}><LogIn size={16} /> Log in</button>}
          </div>
        </header>

        <div className="page-content">
          {(activeSearch || searchYear || searchGenre) && (
            <section className="search-results" id="search-results">
              <div className="section-heading"><div><span className="eyebrow">FIND YOUR SOUND</span><h2>Search results{activeSearch && <span> for “{activeSearch}”</span>}</h2></div><div className="search-filters"><input aria-label="Filter by year" inputMode="numeric" maxLength={4} placeholder="Year" value={searchYear} onChange={(e) => setSearchYear(e.target.value.replace(/\D/g, '').slice(0, 4))} /><select aria-label="Filter by genre" value={searchGenre} onChange={(e) => setSearchGenre(e.target.value)}><option value="">All genres</option><option value="love">Love</option><option value="melody">Melody</option><option value="folk">Folk / Kuthu</option><option value="bgm">BGM / Instrumental</option></select><button className="text-button" onClick={() => { setQuery(''); setActiveSearch(''); setSearchYear(''); setSearchGenre(''); }}>Clear <X size={14} /></button></div></div>
              {searchLoading ? <div className="empty-search" role="status">Searching the connected catalog…</div> : searchResults.length ? <div className="song-list">{searchResults.map((song) => <SongRow key={song.id} song={song} onPlay={playSong} onLike={song.providerUrl ? null : toggleFavorite} liked={favorites.has(song.id)} />)}</div> : <div className="empty-search">No matching songs in the connected catalog yet.</div>}
            </section>
          )}

          <section className="hero">
            <div className="hero-glow" />
            <div className="hero-content">
              <div className="hero-pill"><span className="pulse" /> {isTamil ? 'உங்கள் தமிழ் இசை இல்லம்' : 'YOUR TAMIL MUSIC HOME'}</div>
              <h1>Every era.<br /><em>One feeling.</em></h1>
              <p>பழைய பாடல்களின் நினைவுகள், புதிய பாடல்களின் உற்சாகம்!<br />{!isTamil && <span>Old song memories, new song energy.</span>}</p>
              <div className="hero-buttons"><button className="primary-button" onClick={() => scrollTo('new-releases')}><Play size={17} fill="currentColor" /> {isTamil ? 'இசையை ஆராயுங்கள்' : 'Explore music'}</button><button className="secondary-button" onClick={() => scrollTo('classics')}><Disc3 size={17} /> {isTamil ? 'பழைய பாடல்கள்' : 'The classics'}</button></div>
              <div className="hero-meta"><div className="hero-meta-icon"><Mic2 size={16} /></div><div><b>A sound for every memory</b><span>From timeless melodies to today's releases</span></div></div>
            </div>
            <div className="hero-art" aria-hidden="true">
              <div className="orbit orbit-one" /><div className="orbit orbit-two" />
              <div className="vinyl-disc"><div className="vinyl-groove" /><div className="vinyl-groove groove-two" /><div className="vinyl-label"><AudioLines size={25} /><small>ISAI<br />WORLD</small></div></div>
              <div className="art-note note-one">♫</div><div className="art-note note-two">♪</div>
              <div className="hero-art-caption"><span>THE SOUND OF TAMIL</span><b>இசையே வாழ்க்கை</b></div>
            </div>
            <div className="hero-bottom-line"><span>EST. IN EVERY HEART</span><span>01 — 13</span></div>
          </section>

          <div className="welcome-row"><div><span className="eyebrow">{indiaDay} · {isTamil ? 'இசை தொடர்கிறது' : 'MUSIC NEVER STOPS'}</span><h2>{isTamil ? 'வணக்கம்' : greeting}<span className="gold-dot">.</span></h2><p>{isTamil ? 'உங்கள் அடுத்த விருப்பப் பாடல் இங்கே இருக்கலாம்.' : 'Your next favorite song is somewhere in here.'}</p></div><div className="update-card"><div className="update-icon"><CalendarDays size={19} /></div><div><b>{isTamil ? 'பட்டியல் புதுப்பிப்பு' : 'Catalog updates'}</b><span>{syncStatus?.lastSuccessAt ? `Synced ${new Date(syncStatus.lastSuccessAt).toLocaleString()}` : 'Waiting for licensed catalog connection'}</span></div><span className={`status-dot ${syncStatus?.configured ? 'status-live' : ''}`} /></div></div>
          {error && <div className="notice" role="status"><CircleHelp size={17} /><span>{error}</span><button onClick={() => setError('')} aria-label="Dismiss"><X size={15} /></button></div>}

          {role === 'ADMIN' && <section className="admin-strip"><div><Settings2 size={20} /><div><b>Catalog administration</b><span>{syncStatus?.lastSync?.errorMessage || (syncStatus?.configured ? 'Provider connected — sync on schedule' : 'Configure YOUTUBE_API_KEY or a licensed catalog provider')}</span></div></div><button className="secondary-button small" disabled={busy || !syncStatus?.configured} onClick={syncNow}>{busy ? <LoaderCircle size={15} className="spin" /> : <ArrowDownToLine size={15} />} Sync catalog now</button><div className="admin-logs"><b>Recent synchronization logs</b>{adminLogs.slice(0, 3).map((entry, index) => <span key={`${entry.startedAt}-${index}`}><i className={entry.status === 'SUCCESS' ? 'log-success' : 'log-failed'} />{entry.status} · {entry.songsAdded} added · {entry.songsUpdated} updated{entry.errorMessage ? ` · ${entry.errorMessage}` : ''}</span>)}</div></section>}

          {libraryView && <section className="library-panel" id="library"><div className="section-heading"><div><span className="eyebrow">YOUR PERSONAL LIBRARY</span><h2>{libraryView === 'favorites' ? 'Liked songs' : 'Your playlists'}</h2></div><button className="text-button" onClick={() => setLibraryView('')}>Close <X size={14} /></button></div>
            {libraryView === 'favorites' ? (favoriteSongs.length ? <div className="song-list">{favoriteSongs.map((song) => <SongRow key={song.id} song={song} onPlay={playSong} onLike={toggleFavorite} liked />)}</div> : <div className="empty-search">Songs you favorite will appear here.</div>) : <>
              <form className="playlist-create" onSubmit={createPlaylist}><input value={playlistDraft} onChange={(e) => setPlaylistDraft(e.target.value)} placeholder="Name a new playlist" required maxLength={150} /><button className="primary-button" type="submit"><Plus size={15} /> Create playlist</button></form>
              <div className="playlist-layout"><div className="playlist-list">{playlists.length ? playlists.map((playlist) => <button className={`playlist-item ${String(selectedPlaylist) === String(playlist.id) ? 'playlist-selected' : ''}`} key={playlist.id} onClick={() => openPlaylist(playlist)}><ListMusic size={16} /><span><b>{playlist.name}</b><small>{playlist.song_count ?? 0} songs</small></span></button>) : <p className="empty-playlists">Create a playlist to start collecting songs.</p>}</div>
                {playlistSongs.playlistId === String(selectedPlaylist) && <div className="playlist-track-list">{playlistSongs.songs.map((song) => <SongRow key={song.id} song={song} onPlay={playSong} onLike={toggleFavorite} onRemove={removeFromPlaylist} liked={favorites.has(song.id)} />)}</div>}</div>
              <div className="playlist-add"><div><b>Add a song</b><span>Choose any available song from your catalog.</span></div><select aria-label="Select song to add" defaultValue="" onChange={(e) => { const song = songs.find((item) => item.id === e.target.value); if (song) addToPlaylist(song); e.target.value = ''; }}><option value="" disabled>Select song…</option>{songs.map((song) => <option key={song.id} value={song.id}>{song.title} — {song.singer || song.movie || 'Tamil song'}</option>)}</select></div>
            </>}
          </section>}

          {sections.map(({ title, icon: Icon, key }) => {
            const id = key === 'new' ? 'new-releases' : key === 'old' ? 'classics' : key === 'trending' ? 'trending' : key === 'recent' ? 'recent' : key;
            const list = key === 'recent' ? recent : key === 'daily' ? dailyBeats : key === 'trending' ? trendingSongs : key === 'new'
              ? (todaySongs.length ? todaySongs : [...songs].sort((a, b) => (b.releaseDate || '').localeCompare(a.releaseDate || '')))
              : songs.filter((song) => matching(song, key));
            const visible = expandedSections.has(key) ? list : list.slice(0, 6);
            const critical = key === 'new' || key === 'latest';
            return (
              <section className="music-section" id={id} key={key}>
                <div className="section-heading">
                  <div className="section-title-wrap"><span className={`section-icon icon-${key}`}><Icon size={17} /></span><div><span className="eyebrow">{key === 'new' ? 'FRESH FROM THE STUDIO' : key === 'daily' ? 'TODAY’S TAMIL BEATS · DAILY PLAYLIST' : key === 'old' ? 'TIMELESS BY NATURE' : 'MADE FOR YOUR MOOD'}</span><h2>{language === 'ta' ? tamil[title] || title : title}{key === 'new' && <span className="live-badge"><i /> TODAY</span>}</h2></div></div>
                  {list.length > 5 && <button className="text-button" onClick={() => setExpandedSections((old) => { const next = new Set(old); if (next.has(key)) next.delete(key); else next.add(key); return next; })}>{expandedSections.has(key) ? 'Show less' : 'See all'} <span>{expandedSections.has(key) ? '↑' : '→'}</span></button>}
                </div>
                {visible.length ? <div className="music-grid">{visible.map((song) => <MusicCard key={song.id} song={song} onPlay={playSong} onLike={toggleFavorite} onAdd={token ? addToPlaylist : null} liked={favorites.has(song.id)} isNew={key === 'new' && todaySongs.length > 0} />)}</div> : (
                  <>
                  {!syncStatus?.configured && key === 'latest' && <div className="music-grid"><FeaturedYouTubeCard song={featuredYouTubeSong} onPlay={playSong} /></div>}
                  <div className={`section-empty ${critical ? 'empty-featured' : ''}`}>
                    <div className="empty-disc"><Icon size={20} /></div>
                    <div><b>{key === 'recent' ? (isTamil ? 'உங்கள் இசைப் பயணம் இங்கே தொடங்குகிறது' : 'Your listening story starts here') : key === 'new' ? (isTamil ? 'இன்று புதிய வெளியீடுகள் இல்லை' : 'No new releases today') : (isTamil ? 'உங்கள் பாடல் தொகுப்பு காத்திருக்கிறது' : 'Your collection is waiting')}</b><span>{key === 'recent' ? (isTamil ? 'உங்கள் கேட்கும் வரலாற்றைத் தொடங்க இணைக்கப்பட்ட பட்டியலிலிருந்து ஒரு பாடலை இயக்குங்கள்.' : 'Play a song from the connected catalog to start your listening history.') : syncStatus?.configured ? (isTamil ? 'இணைக்கப்பட்ட பட்டியலில் பொருந்தும் பாடல்கள் இல்லை. அடுத்த ஒத்திசைவுக்குப் பிறகு மீண்டும் பார்க்கவும்.' : 'The connected catalog has no matching songs yet. Check again after its next sync.') : (isTamil ? 'இசைப் பட்டியல் இணைக்கப்படவில்லை. பாடல்களை இந்தச் செயலியில் காட்ட பின்தளத்தில் YOUTUBE_API_KEY அமைக்கவும்; இப்போது கேட்க YouTube-இல் தேடவும்.' : 'The in-app catalog needs a YouTube API key to import songs. You can search YouTube and listen there right now.')}</span>{key !== 'recent' && <a className="youtube-search-link" href={youtubeSearchUrl(key)} target="_blank" rel="noreferrer">{isTamil ? 'YouTube-இல் பாடல்களைத் தேடுங்கள்' : `Find ${title.toLowerCase()} on YouTube`} <span aria-hidden="true">↗</span></a>}</div>
                    {critical && <span className="empty-status"><BadgeCheck size={14} /> {syncStatus?.configured ? 'Catalog connected' : 'Provider not connected'}</span>}
                  </div>
                  </>
                )}
              </section>
            );
          })}
          <footer className="footer"><a className="footer-brand" href="#home">இசை உலகம்</a><span>Music that feels like home.</span><span>Playback uses the source provider's player and terms.</span></footer>
        </div>
      </main>

      <audio ref={audioRef} onTimeUpdate={(e) => setPosition(e.currentTarget.currentTime)} onLoadedMetadata={(e) => setDuration(e.currentTarget.duration || 0)} onEnded={() => repeat ? (audioRef.current.currentTime = 0, audioRef.current.play()) : stepSong(1)} onPause={() => setIsPlaying(false)} onPlay={() => setIsPlaying(true)} />
      {current && <div className="player">
        <div className="player-track"><Artwork song={current} small /><div className="player-song"><b>{current.title}</b><span>{current.singer || current.movie || 'Tamil music'}</span></div>{current.lyricsUrl && <a className="lyrics-link" href={current.lyricsUrl} target="_blank" rel="noreferrer">Lyrics</a>}{!current.isFeaturedFallback && !current.providerUrl && <button className={`heart-button ${favorites.has(current.id) ? 'liked' : ''}`} onClick={() => toggleFavorite(current)} aria-label="Favorite"><Heart size={17} fill={favorites.has(current.id) ? 'currentColor' : 'none'} /></button>}</div>
        {isAppleMusicPlayback(current) ? <button className="player-center youtube-player-hint" onClick={() => setApplePlayerOpen(true)}>Open Apple Music player</button> : isYouTubePlayback(current) ? <button className="player-center youtube-player-hint" onClick={() => setYoutubePlayerOpen(true)}>Open YouTube player controls</button> : <div className="player-center"><div className="player-controls"><button aria-label="Shuffle" className={shuffle ? 'control-active' : ''} onClick={() => setShuffle(!shuffle)}><Shuffle size={15} /></button><button aria-label="Previous" onClick={() => stepSong(-1)}><SkipBack size={18} fill="currentColor" /></button><button className="player-play" aria-label={isPlaying ? 'Pause' : 'Play'} onClick={togglePlay}>{isPlaying ? <Pause size={17} fill="currentColor" /> : <Play size={17} fill="currentColor" />}</button><button aria-label="Next" onClick={() => stepSong(1)}><SkipForward size={18} fill="currentColor" /></button><button aria-label="Repeat" className={repeat ? 'control-active' : ''} onClick={() => setRepeat(!repeat)}>↻</button></div><div className="timeline"><span>{formatTime(position)}</span><input type="range" min="0" max={duration || 100} value={Math.min(position, duration || 100)} onChange={(e) => { if (audioRef.current) audioRef.current.currentTime = Number(e.target.value); }} aria-label="Seek" /><span>{formatTime(duration)}</span></div></div>}
        <div className="player-volume">{!isYouTubePlayback(current) && !isAppleMusicPlayback(current) && <><Volume2 size={17} /><input type="range" min="0" max="1" step="0.01" value={volume} onChange={(e) => setVolume(Number(e.target.value))} aria-label="Volume" /></>}<button aria-label="Close player" className="close-player" onClick={() => { audioRef.current?.pause(); setYoutubePlayerOpen(false); setApplePlayerOpen(false); setCurrent(null); }}><X size={16} /></button></div>
      </div>}

      {applePlayerOpen && isAppleMusicPlayback(current) && <div className="youtube-player-backdrop" role="dialog" aria-modal="true" aria-label={`Apple Music player: ${current.title}`} onMouseDown={(event) => { if (event.target === event.currentTarget) setApplePlayerOpen(false); }}><section className="youtube-player-dialog"><div className="youtube-player-heading"><div><b>{current.title}</b><span>{current.singer}</span></div><button aria-label="Close Apple Music player" onClick={() => setApplePlayerOpen(false)}><X size={18} /></button></div><div className="youtube-frame apple-music-frame"><iframe src={appleMusicEmbedUrl(current)} title={`Apple Music preview: ${current.title}`} allow="autoplay *; encrypted-media *; fullscreen *; clipboard-write" referrerPolicy="strict-origin-when-cross-origin" /></div><p>Apple previews may require sign-in or may be unavailable for some tracks. Try the official track page or search for an authorized upload:</p><div className="provider-play-links"><a className="apple-music-link" href={current.providerUrl} target="_blank" rel="noreferrer">Open this track on Apple Music</a><a className="youtube-music-link" href={youtubeTrackSearchUrl(current)} target="_blank" rel="noreferrer">Find this track on YouTube</a></div></section></div>}

      {youtubePlayerOpen && isYouTubePlayback(current) && <div className="youtube-player-backdrop" role="dialog" aria-modal="true" aria-label={`YouTube player: ${current.title}`} onMouseDown={(event) => { if (event.target === event.currentTarget) setYoutubePlayerOpen(false); }}><section className="youtube-player-dialog"><div className="youtube-player-heading"><div><b>{current.title}</b><span>{current.singer}</span></div><button aria-label="Close YouTube player" onClick={() => setYoutubePlayerOpen(false)}><X size={18} /></button></div><div className="youtube-frame"><iframe src={`${current.playbackUrl}?controls=1&playsinline=1&rel=0`} title={`YouTube video: ${current.title}`} allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerPolicy="strict-origin-when-cross-origin" allowFullScreen /></div><p>If YouTube shows a playback error in the embedded player, open the video directly on YouTube.</p><a className="youtube-video-link" href={current.providerUrl || youtubeTrackSearchUrl(current)} target="_blank" rel="noreferrer">{current.providerUrl ? 'Open this video on YouTube' : 'Search this song on YouTube'}</a></section></div>}

      {authOpen && <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setAuthOpen(false)}><form className="auth-modal" onSubmit={handleAuth}><button type="button" className="modal-close" onClick={() => setAuthOpen(false)}><X size={18} /></button><div className="auth-logo"><AudioLines size={20} /></div><span className="eyebrow">WELCOME TO ISAI WORLD</span><h2>{isRegister ? 'Create your account' : 'Come on in'}</h2><p>Save favorites and keep your listening close.</p>{error && <div className="auth-error" role="alert">{error}</div>}<label>Email address<input type="email" name="email" autoComplete="email" required placeholder="you@example.com" /></label><label>Password<input type="password" name="password" minLength="10" autoComplete={isRegister ? 'new-password' : 'current-password'} required placeholder="At least 10 characters" /></label><button type="submit" className="primary-button auth-submit" disabled={busy}>{busy ? <LoaderCircle size={17} className="spin" /> : null}{isRegister ? 'Create account' : 'Log in'}</button><button type="button" className="switch-auth" onClick={() => { setError(''); setIsRegister(!isRegister); }}>{isRegister ? 'Already have an account? Log in' : 'New here? Create an account'}</button></form></div>}
    </div>
  );
}

function Artwork({ song, small = false }) {
  return song.artworkUrl
    ? <img className={`artwork ${small ? 'artwork-small' : ''}`} src={song.artworkUrl} alt="" loading="lazy" />
    : <div className={`artwork artwork-fallback ${small ? 'artwork-small' : ''}`}><AudioLines size={small ? 19 : 25} /><span>இசை</span></div>;
}

function MusicCard({ song, onPlay, onLike, onAdd, liked, isNew }) {
  return <article className="music-card">
    <div className="card-art"><Artwork song={song} />{isNew && <span className="new-tag">NEW</span>}{onAdd && <button className="card-add" onClick={() => onAdd(song)} aria-label={`Add ${song.title} to selected playlist`}><Plus size={15} /></button>}<button className="card-play" onClick={() => onPlay(song)} aria-label={`Play ${song.title}`}><Play size={18} fill="currentColor" /></button></div>
    <div className="card-info"><div className="card-title-line"><b title={song.title}>{song.title}</b><button className={`mini-heart ${liked ? 'liked' : ''}`} aria-label="Favorite" onClick={() => onLike(song)}><Heart size={15} fill={liked ? 'currentColor' : 'none'} /></button></div><span>{[song.singer, song.movie || song.album].filter(Boolean).join(' · ') || 'Tamil song'}</span></div>
  </article>;
}

function FeaturedYouTubeCard({ song, onPlay }) {
  return <article className="music-card featured-youtube-card">
    <div className="card-art"><Artwork song={song} /><span className="new-tag">OFFICIAL VIDEO</span><button className="card-play" onClick={() => onPlay(song)} aria-label={`Play ${song.title}`}><Play size={18} fill="currentColor" /></button></div>
    <div className="card-info"><div className="card-title-line"><b title={song.title}>{song.title}</b></div><span>Verified channel: Sony Music South · Opens in YouTube player</span></div>
  </article>;
}

function SongRow({ song, onPlay, onLike, onRemove, liked }) {
  const appleResult = song.providerUrl?.startsWith('https://music.apple.com/');
  return <div className="song-row"><button className="row-play" onClick={() => onPlay(song)} aria-label={`Open player for ${song.title}`}><Play size={15} fill="currentColor" /></button><Artwork song={song} small /><div className="row-details"><b>{song.title}</b><span>{[song.singer, song.movie, song.composer].filter(Boolean).join(' · ')}</span>{song.providerUrl && <><a className="provider-attribution" href={song.providerUrl} target="_blank" rel="noreferrer">{appleResult ? '30-second preview · Open in Apple Music' : 'Open video on YouTube'}</a>{appleResult && <a className="youtube-result-link" href={youtubeTrackSearchUrl(song)} target="_blank" rel="noreferrer">Search YouTube</a>}</>}</div><span className="row-date">{song.releaseDate || ''}</span>{onLike && !song.providerUrl && <button className={`mini-heart ${liked ? 'liked' : ''}`} onClick={() => onLike(song)} aria-label="Toggle favorite"><Heart size={16} fill={liked ? 'currentColor' : 'none'} /></button>}{onRemove && <button className="remove-track" onClick={() => onRemove(song)} aria-label={`Remove ${song.title} from playlist`}><X size={15} /></button>}</div>;
}

function formatTime(seconds) {
  if (!Number.isFinite(seconds)) return '0:00';
  return `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`;
}

export default App;
